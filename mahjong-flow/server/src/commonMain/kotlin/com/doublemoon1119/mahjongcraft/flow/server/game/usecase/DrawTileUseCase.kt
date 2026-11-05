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
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.DrawSource
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.drawFromLiveWall
import kotlin.uuid.Uuid

/**
 * 摸牌的實例化用例。
 *
 * 負責處理玩家的摸牌請求，包含回合驗證、牌山狀態更新以及快照與事件的同步。摸牌後在同一筆寫入內交給
 * [DrawFollowUpApplier] 依規則自動處理，例如摸到花牌時自動移出並補牌。
 *
 * @property componentRegistry 正式組件定義與純效果來源。
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
    private val componentRegistry: ComponentRegistry,
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
                    val draw = state.drawFromLiveWall(playerId)
                    if (draw == null) {
                        state to Outcome.Error(GameError.WallExhausted(gameId))
                    } else {
                        val module = moduleRegistry.getModule(state.config)
                        val drawnPlayer = draw.tableState.currentPlayer
                        val updatedPlayer = RuleComponentChecker(componentRegistry, module.id).applyPlayerPatch(
                            tableState = state,
                            player = drawnPlayer,
                            patch = module.onPlayerDrew(drawnPlayer),
                            source = "onPlayerDrew",
                        )
                        val drawnState = draw.tableState.copy(players = draw.tableState.players.map { if (it.id == playerId) updatedPlayer else it })
                        val followUp = DrawFollowUpApplier.run(drawnState, playerId, DrawSource.LiveWall, listOf(draw.tile), module, componentRegistry = componentRegistry)

                        followUp.tableState to Outcome.Success(DrawResult(draw.tile, drawnState, followUp))
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

        // 4. 依序呈現摸牌與摸牌後的自動處理
        presentationPublisher.publishTableSteps(gameId, result.followUp.tableSteps(playerId, result.drawnState))

        return Outcome.Success(Unit)
    }

    /**
     * `update` 區塊內部使用的中繼結果。
     *
     * @property drawnTile 這次從活牌摸到的牌。
     * @property drawnState 摸牌之後、自動處理之前的桌況。
     * @property followUp 摸牌之後的自動處理；它的桌況是這次交易提交的權威桌況。
     */
    private data class DrawResult(
        val drawnTile: IdentifiedTile,
        val drawnState: TableState,
        val followUp: DrawFollowUpApplier.Result,
    )
}
