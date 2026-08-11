package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 捨牌的實例化用例。
 *
 * 負責處理玩家的捨牌請求，包含回合驗證、手牌與牌河狀態更新，以及快照與事件的同步。
 *
 * 捨牌後其他玩家是否有資格吃/碰/槓/榮和這張牌、一炮多響時依 [com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy]
 * 決定實際開放給誰、[RonResolution.ABORTIVE_DRAW] 是否直接觸發流局，這些邏輯與 [DeclareRiichiUseCase]
 * （立直宣告牌）共用，交給 [DiscardReactionResolver] 處理，詳見其 KDoc。
 *
 * 除了一炮多響判定為流局之外，這張捨牌若沒有任何人可以吃/碰/槓/榮和，還會額外檢查是否構成四風連打
 * （[com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule.resolveSuufonRenda]）。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器。
 * @property gameSnapshotRepository 對局快照數據倉庫。
 * @property eventPublisher 對局通知服務。
 */
@Factory
class DiscardTileUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val gameSnapshotRepository: GameSnapshotRepository,
    @Provided private val eventPublisher: GameEventPublisher,
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
                        val discardedTile = discardResult.tile
                        val updatedPlayer = state.currentPlayer
                            .copy(
                                hand = discardResult.hand,
                                discardPile = state.currentPlayer.discardPile.discardTile(discardedTile),
                            )
                            .recordAction(GameAction.Discard(tileId))
                        val updatedPlayers = state.players.map { if (it.id == playerId) updatedPlayer else it }
                        val stateAfterDiscard = state.copy(players = updatedPlayers)

                        val module = moduleRegistry.getModule(state.config)
                        val resolved = DiscardReactionResolver.resolve(state, stateAfterDiscard, module, playerId, discardedTile)

                        // 沒有觸發一炮多響流局、也沒有人可反應時，額外檢查是否構成四風連打。
                        val suufonReason = if (resolved.abortiveDrawReason == null && resolved.tableState.pendingReaction == null) {
                            module.resolveSuufonRenda(resolved.tableState)
                        } else {
                            null
                        }
                        val finalResult = if (suufonReason != null) {
                            resolved.copy(
                                tableState = resolved.tableState.copy(
                                    players = resolved.tableState.players.map { it.recordAction(GameAction.ExhaustiveDraw(suufonReason)) },
                                ),
                                abortiveDrawReason = suufonReason,
                            )
                        } else {
                            resolved
                        }

                        finalResult.tableState to Outcome.Success(finalResult)
                    }
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.tableState

        // 2. 同步快照給所有正在觀察的玩家
        val observers = gameSnapshotRepository.getAllObservers(gameId)
        observers.forEach { observerId ->
            gameSnapshotRepository.setSnapshot(observerId, newState.toSnapshot(setOf(observerId)))
        }

        // 3. 通知對局內的所有玩家；流局有觸發時，先廣播捨牌事件、再接著廣播流局事件
        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, playerId, GameAction.Discard(tileId))
            result.abortiveDrawReason?.let { reason ->
                eventPublisher.publish(gameId, player.id, playerId, GameAction.ExhaustiveDraw(reason))
            }
        }

        return Outcome.Success(Unit)
    }
}
