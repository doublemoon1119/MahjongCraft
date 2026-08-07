package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
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
 * 一炮多響（同一張捨牌同時被多位玩家榮和）依 [com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy]
 * 決定實際開放給誰：恰有 2 人可榮和時查
 * [com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy.doubleRonResolution]，3 人（含）以上
 * 則查 [com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy.tripleRonResolution]——
 * [RonResolution.ALL_WINNERS] 全部開放；[RonResolution.NEAREST_WINNER] 只開放依
 * [com.doublemoon1119.mahjongcraft.logic.table.TableState.nearestPlayerInTurnOrder] 判定、
 * 順位最接近放銃者下家的那一位（頭跳）；[RonResolution.ABORTIVE_DRAW] 這張捨牌會讓本局直接結束
 * （途中流局），吃/碰/槓資格一併作廢、不開反應視窗，把 [GameAction.ExhaustiveDraw] 記錄進全員
 * 的 `actionHistory`，讓 [AdvanceRoundUseCase] 既有的連莊判斷式自動得出正確結果，不結算任何點數
 * （供託延續到下一局）。
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

                        // 一炮多響：依規則設定決定實際開放榮和資格給誰（4 人對局扣除放銃者後最多
                        // 同時有 3 人可榮和，故只需區分雙響／三響兩種情境）。
                        val ronResolution = when (ronEligiblePlayerIds.size) {
                            0, 1 -> null
                            2 -> state.config.multiRonPolicy.doubleRonResolution
                            else -> state.config.multiRonPolicy.tripleRonResolution
                        }
                        val ronWinningPlayerIds = when (ronResolution) {
                            null, RonResolution.ALL_WINNERS -> ronEligiblePlayerIds
                            RonResolution.NEAREST_WINNER ->
                                setOf(stateAfterDiscard.nearestPlayerInTurnOrder(playerId, ronEligiblePlayerIds))
                            RonResolution.ABORTIVE_DRAW -> emptySet()
                        }

                        val abortiveDrawReason = if (ronResolution == RonResolution.ABORTIVE_DRAW) {
                            module.resolveMultiRonAbortiveDraw()
                        } else {
                            null
                        }

                        val newState = if (abortiveDrawReason != null) {
                            // 三家和了流局（途中流局）：這張捨牌已經讓本局結束，吃/碰/槓資格一併作廢
                            // （不開反應視窗）、不結算任何點數，供託延續到下一局。把 ExhaustiveDraw
                            // 記錄進全員的 actionHistory，讓 AdvanceRoundUseCase 既有的判斷式自動
                            // 得出「連莊」的結果，不需要額外分支。
                            stateAfterDiscard.copy(
                                players = stateAfterDiscard.players.map { it.recordAction(GameAction.ExhaustiveDraw(abortiveDrawReason)) },
                            )
                        } else {
                            val eligiblePlayerIds = meldEligiblePlayerIds + ronWinningPlayerIds
                            if (eligiblePlayerIds.isEmpty()) {
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
                        }

                        newState to Outcome.Success(DiscardOutcome(newState, abortiveDrawReason))
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
            gameSnapshotRepository.setSnapshot(observerId, newState.toSnapshot(observerId))
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

    /**
     * `update` 區塊內部使用的中繼結果，讓 [abortiveDrawReason] 能跟著 [tableState] 一起帶出
     * `gameRepository.update` 的作用域，供廣播事件時使用（[abortiveDrawReason] 不是 [TableState]
     * 的欄位，無法在作用域外從 [tableState] 反推）。
     */
    private data class DiscardOutcome(val tableState: TableState, val abortiveDrawReason: ExhaustiveDrawReason?)
}
