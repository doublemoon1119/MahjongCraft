package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 捨牌的實例化用例。
 *
 * 負責處理玩家的捨牌請求，包含回合驗證、手牌與牌河狀態更新、推進下一位玩家，
 * 以及快照與事件的同步。
 *
 * 已知、刻意的簡化：本用例捨牌後直接推進到下一位玩家，不會開啟「其他人是否要
 * 吃/碰/槓/榮和」的反應視窗。此反應視窗將於鳴牌（Chi/Pon/Kan）與榮和（Ron）的
 * use case 一起規劃時再處理，詳見 `docs/temp/game-use-case-architecture.md`。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property gameSnapshotRepository 對局快照數據倉庫。
 * @property eventPublisher 對局通知服務。
 */
@Factory
class DiscardTileUseCase(
    private val gameRepository: GameRepository,
    private val gameSnapshotRepository: GameSnapshotRepository,
    @Provided private val eventPublisher: GameEventPublisher
) {
    /**
     * 執行捨牌邏輯。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起捨牌請求的玩家 Uuid。
     * @param tileId 欲捨棄牌的唯一識別碼。
     * @return 捨牌結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid, playerId: Uuid, tileId: Uuid): Outcome<Unit, GameError> {
        // 1. 以原子方式讀取桌況、驗證業務規則並寫回
        val outcome = gameRepository.update(gameId) { state ->
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                state.players.none { it.id == playerId } ->
                    state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))
                state.currentPlayer.id != playerId ->
                    state to Outcome.Error(GameError.NotPlayersTurn(playerId, gameId))
                state.currentPlayer.hand.lastDrawn == null ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Discard(tileId)))
                else -> {
                    val discardResult = state.currentPlayer.hand.discardById(tileId)
                    if (discardResult == null) {
                        state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Discard(tileId)))
                    } else {
                        val updatedPlayer = state.currentPlayer
                            .copy(
                                hand = discardResult.hand,
                                discardPile = state.currentPlayer.discardPile.discardTile(discardResult.tile)
                            )
                            .recordAction(GameAction.Discard(tileId))
                        val updatedPlayers = state.players.map { if (it.id == playerId) updatedPlayer else it }
                        val newState = state.copy(
                            players = updatedPlayers,
                            currentPlayerIndex = (state.currentPlayerIndex + 1) % state.playerCount
                        )

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
            eventPublisher.publish(gameId, player.id, playerId, GameAction.Discard(tileId))
        }

        return Outcome.Success(Unit)
    }
}
