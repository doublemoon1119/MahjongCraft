package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
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
 * @property gameSnapshotRepository 對局快照數據倉庫。
 * @property eventPublisher 對局通知服務。
 */
@Factory
class DrawTileUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val gameSnapshotRepository: GameSnapshotRepository,
    @Provided private val eventPublisher: GameEventPublisher
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
                                .recordAction(GameAction.Draw)
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
        val observers = gameSnapshotRepository.getAllObservers(gameId)
        observers.forEach { observerId ->
            gameSnapshotRepository.setSnapshot(observerId, newState.toSnapshot(observerId))
        }

        // 3. 通知對局內的所有玩家
        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, playerId, GameAction.Draw)
        }

        return Outcome.Success(Unit)
    }
}
