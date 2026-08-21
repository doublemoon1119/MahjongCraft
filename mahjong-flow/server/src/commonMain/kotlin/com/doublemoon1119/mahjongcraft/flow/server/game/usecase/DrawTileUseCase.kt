package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 摸牌的實例化用例。
 *
 * 負責處理玩家的摸牌請求，包含回合驗證、牌山狀態更新以及快照與事件的同步。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的規則模組。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器。
 */
@Factory
class DrawTileUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    @Provided private val eventPublisher: GameEventPublisher,
    @Provided private val presentationPublisher: GamePresentationPublisher,
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
        val outcome = gameRepository.update(gameId) { state ->
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                state.players.none { it.id == playerId } ->
                    state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))
                state.currentPlayer.id != playerId ->
                    state to Outcome.Error(GameError.NotPlayersTurn(playerId, gameId))
                state.currentPlayer.hand.lastDrawn != null ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Draw))
                else -> {
                    val (tile, newWall) = state.tileWall.draw()
                    if (tile == null) {
                        state to Outcome.Error(GameError.WallExhausted(gameId))
                    } else {
                        val module = moduleRegistry.getModule(state.config)
                        val updatedPlayer = module.onPlayerDrew(
                            state.currentPlayer
                                .copy(hand = state.currentPlayer.hand.copy(lastDrawn = tile))
                                .clearPassedTiles()
                                .recordAction(GameAction.Draw),
                        )
                        val updatedPlayers = state.players.map { if (it.id == playerId) updatedPlayer else it }
                        val newState = state.copy(tileWall = newWall, players = updatedPlayers)

                        newState to Outcome.Success(newState)
                    }
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val newState = (outcome as Outcome.Success).value

        // 2. 同步快照給所有正在觀察的玩家
        snapshotSynchronizer.syncAll(gameId)

        // 3. 通知對局內的所有玩家
        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, playerId, GameAction.Draw)
        }

        // 4. 觸發平台呈現層：把摸到的牌從牌牆移到摸牌位（副露/積棒不受摸牌影響，仍要一併帶上讓手牌
        // 讓開偏移量算得準）；animateDrawnTile 傳 true 播放摸牌動畫，理由見
        // GamePresentationPublisher.publishPlayerAreaUpdated 的同名參數 KDoc。
        val seatIndex = newState.players.indexOfFirst { it.id == playerId }
        val drawnPlayer = newState.players[seatIndex]
        val dealerSeatIndex = newState.players.indexOfFirst { it.currentWind == Wind.EAST }
        presentationPublisher.publishPlayerAreaUpdated(
            gameId,
            seatIndex,
            drawnPlayer.hand.tiles.map { it.id },
            drawnPlayer.hand.lastDrawn?.id,
            drawnPlayer.hand.melds.map { it.toPresentation(newState.config.revealsClosedKanTiles) },
            comboStickCount = if (seatIndex == dealerSeatIndex) newState.comboCount else 0,
            animateDrawnTile = true,
        )
        // 牌山剩餘張數每次摸牌都會變，桌面局況顯示要跟著更新。
        presentationPublisher.publishRoundInfoUpdated(
            gameId,
            newState.prevalentWind,
            localRoundNumber = newState.localRoundNumber,
            comboCount = newState.comboCount,
            wallRemainingCount = newState.tileWall.remainingCount,
        )

        return Outcome.Success(Unit)
    }
}
