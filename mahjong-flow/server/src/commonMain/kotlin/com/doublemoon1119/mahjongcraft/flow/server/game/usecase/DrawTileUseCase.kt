package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.history.acceptedActionHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.isDrawWindowOpenFor
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.DrawSource
import kotlin.uuid.Uuid

/**
 * 摸牌的實例化用例。
 *
 * 負責處理玩家的摸牌請求，包含回合驗證、牌山狀態更新以及快照與事件的同步。摸牌後在同一筆寫入內交給
 * [DrawFollowUpApplier] 依規則自動處理，例如摸到花牌時自動移出並補牌。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的規則模組。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器。
 */
class DrawTileUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val eventPublisher: GameEventPublisher,
    private val presentationPublisher: GamePresentationPublisher,
) {
    /**
     * 執行摸牌邏輯。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起摸牌請求的玩家 Uuid。
     * @return 摸牌結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid, playerId: Uuid): Outcome<Unit, GameError> {
        // 1. 以原子方式讀取桌況、驗證業務規則並寫回
        val outcome = gameRepository.update(
            gameId,
            history = { before, after, result ->
                if (result is Outcome.Success && before != null && after != null) {
                    listOf(acceptedActionHistoryDraft(playerId, GameAction.Draw, before, after, listOf(result.value.drawnTile.id))) +
                        result.value.followUp.historyDrafts(playerId, before, after)
                } else {
                    emptyList<HistoryEventDraft>()
                }
            },
        ) { state ->
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                state.players.none { it.id == playerId } ->
                    state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))
                state.currentPlayer.id != playerId ->
                    state to Outcome.Error(GameError.NotPlayersTurn(playerId, gameId))
                !state.isDrawWindowOpenFor(playerId) ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Draw))
                else -> {
                    val (tile, newWall) = state.tileWall.draw()
                    if (tile == null) {
                        state to Outcome.Error(GameError.WallExhausted(gameId))
                    } else {
                        val module = moduleRegistry.getModule(state.config)
                        val drawnPlayer = state.currentPlayer
                            .copy(hand = state.currentPlayer.hand.copy(lastDrawn = tile))
                            .clearPassedTiles()
                            .recordAction(GameAction.Draw)
                        val updatedPlayer = RuleHookResultValidator.requireRuleStateOnly(
                            before = drawnPlayer,
                            after = module.onPlayerDrew(drawnPlayer),
                            hookName = "onPlayerDrew",
                        )
                        val updatedPlayers = state.players.map { if (it.id == playerId) updatedPlayer else it }
                        val remainingWallTileIds = (newWall.getAllTiles() + state.reservedWallTiles)
                            .mapTo(mutableSetOf()) { it.id }
                        val drawnState = state.copy(
                            tileWall = newWall,
                            players = updatedPlayers,
                            physicalWallLayout = state.physicalWallLayout?.retainOnly(remainingWallTileIds),
                        )
                        val followUp = DrawFollowUpApplier.run(drawnState, playerId, DrawSource.LiveWall, module)

                        followUp.tableState to Outcome.Success(DrawResult(tile, followUp))
                    }
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.followUp.tableState

        // 2. 同步快照給所有正在觀察的玩家
        snapshotSynchronizer.syncAll(gameId)

        // 3. 通知在場玩家與旁觀者
        val seatedPlayerIds = newState.players.map { it.id }
        (listOf(GameAction.Draw) + result.followUp.publishedActions).forEach { action ->
            eventPublisher.publishToAllObservers(gameId, seatedPlayerIds, playerId, action)
        }

        // 4. 觸發平台呈現層：把摸到的牌從牌牆移到摸牌位（副露/積棒不受摸牌影響，仍要一併帶上讓手牌
        // 讓開偏移量算得準）；這是真正的摸牌，isNewlyDrawn 傳 true，見
        // GamePresentationPublisher.publishPlayerTilesUpdated 的同名參數 KDoc。
        val module = moduleRegistry.getModule(newState.config)
        presentationPublisher.publishPlayerAreaAfterDraw(gameId, playerId, result.followUp, module)
        // 牌山剩餘張數每次摸牌都會變，局況資訊要跟著更新——這份呈現是「找到既有的就地更新」
        // 模式，沒帶上完整內容就會把之前顯示的內容覆蓋回空清單。
        presentationPublisher.publishRoundInfoUpdated(gameId, newState)

        return Outcome.Success(Unit)
    }

    /**
     * `update` 區塊內部使用的中繼結果。
     *
     * @property drawnTile 這次從活牌摸到的牌。
     * @property followUp 摸牌之後的自動處理；它的桌況是這次交易提交的權威桌況。
     */
    private data class DrawResult(
        val drawnTile: IdentifiedTile,
        val followUp: DrawFollowUpApplier.Result,
    )
}
