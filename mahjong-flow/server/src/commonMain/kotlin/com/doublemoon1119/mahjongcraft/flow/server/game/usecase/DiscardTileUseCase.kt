package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 捨牌的實例化用例。
 *
 * 負責處理玩家的捨牌請求，包含回合驗證、手牌與牌河狀態更新，以及快照與事件的同步。
 *
 * 捨牌後會計算其他玩家是否有資格吃/碰/槓，或榮和這張牌：若沒有任何人有資格，直接推進到下一位玩家
 * （與先前行為相同）；若有人有資格，則改為開啟 [PendingReaction] 反應視窗、暫緩推進回合，交由
 * [RespondToDiscardUseCase] 處理後續的回應與結算。
 *
 * 一炮多響（同一張捨牌同時被多位玩家榮和）本單位明確排除：若計算出有超過一位玩家可榮和這張牌，
 * 本輪對所有人都不開放榮和資格（吃/碰/槓資格不受影響，各自獨立判斷）——避免在缺乏完整多家和結算
 * 規則的情況下，任意挑其中一人視為「唯一贏家」而產生錯誤結算。多家和支援留給獨立的未來單位。
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
                        val validator = module.createLegalActionValidator()

                        // 先為每位其他玩家各算一次合法動作清單，因為榮和資格需要「先看過全部人」才能
                        // 判斷是否為一炮多響，不能像吃/碰/槓一樣邊算邊篩。
                        val legalActionsByOtherPlayer = updatedPlayers
                            .filter { it.id != playerId }
                            .associate { otherPlayer ->
                                otherPlayer.id to validator.getLegalActions(
                                    tableState = stateAfterDiscard,
                                    player = otherPlayer,
                                    sourceAction = GameAction.Discard(tileId),
                                    sourceDirection = stateAfterDiscard.relativeDirectionOf(otherPlayer.id, playerId),
                                    incomingTile = discardedTile,
                                )
                            }

                        val ronEligiblePlayerIds = legalActionsByOtherPlayer
                            .filterValues { actions -> actions.any { it is GameAction.Ron } }
                            .keys
                        val meldEligiblePlayerIds = legalActionsByOtherPlayer
                            .filterValues { actions -> actions.any { it is GameAction.Chi || it is GameAction.Pon || it is GameAction.Kan } }
                            .keys

                        // 一炮多響防呆：超過一人可榮和同一張牌時，本輪對所有人都不開放榮和資格。
                        val eligiblePlayerIds = meldEligiblePlayerIds + if (ronEligiblePlayerIds.size == 1) {
                            ronEligiblePlayerIds
                        } else {
                            emptySet()
                        }

                        val newState = if (eligiblePlayerIds.isEmpty()) {
                            stateAfterDiscard.copy(currentPlayerIndex = (state.currentPlayerIndex + 1) % state.playerCount)
                        } else {
                            stateAfterDiscard.copy(
                                pendingReaction = PendingReaction(
                                    discarderId = playerId,
                                    tileId = discardedTile.id,
                                    eligiblePlayerIds = eligiblePlayerIds,
                                ),
                            )
                        }

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
