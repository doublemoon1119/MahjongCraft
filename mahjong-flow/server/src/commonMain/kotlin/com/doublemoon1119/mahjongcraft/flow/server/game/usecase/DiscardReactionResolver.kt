package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 依一張剛打出的牌，計算其他玩家的吃/碰/槓/榮和資格，並套用結果到桌況的共用邏輯。
 *
 * 供 [DiscardTileUseCase]（一般捨牌）與 [DeclareRiichiUseCase]（立直宣告牌）共用——兩者除了
 * 「捨牌前套用的規則特有狀態變化」不同（前者單純捨牌；後者還套用立直宣告的玩家/動態狀態）之外，
 * 捨牌後「其他玩家能不能反應這張牌」的計算完全相同，不應該各自重寫一份。
 *
 * 一炮多響（同一張捨牌同時被多位玩家榮和）依 [MultiRonPolicy] 決定實際開放給誰：恰有 2 人可榮和時查
 * [MultiRonPolicy.doubleRonResolution]，3 人（含）以上則查 [MultiRonPolicy.tripleRonResolution]——
 * [RonResolution.ALL_WINNERS] 全部開放；[RonResolution.NEAREST_WINNER] 只開放依
 * [TableState.nearestPlayerInTurnOrder] 判定、
 * 順位最接近放銃者下家的那一位（頭跳）；[RonResolution.ABORTIVE_DRAW] 這張捨牌會讓本局直接結束
 * （途中流局），吃/碰/槓資格一併作廢、不開反應視窗，把 [GameAction.ExhaustiveDraw] 記錄進全員
 * 的 `actionHistory`，不結算任何點數（供託延續到下一局）。
 */
internal object DiscardReactionResolver {

    /**
     * 計算並套用一張剛打出的牌的反應結果。
     *
     * @param stateBeforeDiscard 捨牌前的桌況，用於取得原本的 `currentPlayerIndex` 以計算下一位玩家。
     * @param stateAfterDiscard 捨牌（含呼叫端套用的規則特有狀態變化）後的桌況。
     * @param module 該對局採用的規則模組。
     * @param discarderId 打出 [discardedTile] 的玩家 Uuid。
     * @param discardedTile 剛打出的牌。
     * @return 套用反應資格判定後的結果。
     */
    fun resolve(
        stateBeforeDiscard: TableState,
        stateAfterDiscard: TableState,
        module: MahjongRuleModule<*>,
        discarderId: Uuid,
        discardedTile: IdentifiedTile,
    ): Result {
        val validator = module.createLegalActionValidator()

        // 先為每位其他玩家各算一次合法動作清單，因為榮和資格需要「先看過全部人」才能
        // 判斷是否為一炮多響，不能像吃/碰/槓一樣邊算邊篩。
        val legalActionsByOtherPlayer = stateAfterDiscard.players
            .filter { it.id != discarderId }
            .associate { otherPlayer ->
                otherPlayer.id to validator.getLegalActions(
                    tableState = stateAfterDiscard,
                    player = otherPlayer,
                    sourceAction = GameAction.Discard(discardedTile.id),
                    sourceDirection = stateAfterDiscard.relativeDirectionOf(otherPlayer.id, discarderId),
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
            2 -> stateBeforeDiscard.config.multiRonPolicy.doubleRonResolution
            else -> stateBeforeDiscard.config.multiRonPolicy.tripleRonResolution
        }
        val ronWinningPlayerIds = when (ronResolution) {
            null, RonResolution.ALL_WINNERS -> ronEligiblePlayerIds
            RonResolution.NEAREST_WINNER ->
                setOf(stateAfterDiscard.nearestPlayerInTurnOrder(discarderId, ronEligiblePlayerIds))

            RonResolution.ABORTIVE_DRAW -> emptySet()
        }

        val abortiveDrawReason = if (ronResolution == RonResolution.ABORTIVE_DRAW) {
            module.resolveMultiRonAbortiveDraw()
        } else {
            null
        }

        val newState = if (abortiveDrawReason != null) {
            // 途中流局：這張捨牌已經讓本局結束，吃/碰/槓資格一併作廢（不開反應視窗）、不結算任何
            // 點數，供託延續到下一局。把 ExhaustiveDraw 記錄進全員的 actionHistory，讓
            // AdvanceRoundUseCase 既有的判斷式自動得出「連莊」的結果，不需要額外分支。
            stateAfterDiscard.copy(
                players = stateAfterDiscard.players.map { it.recordAction(GameAction.ExhaustiveDraw(abortiveDrawReason)) },
            )
        } else {
            val eligiblePlayerIds = meldEligiblePlayerIds + ronWinningPlayerIds
            if (eligiblePlayerIds.isEmpty()) {
                stateAfterDiscard.copy(currentPlayerIndex = (stateBeforeDiscard.currentPlayerIndex + 1) % stateBeforeDiscard.playerCount)
            } else {
                stateAfterDiscard.copy(
                    pendingReaction = PendingReaction(
                        discarderId = discarderId,
                        tileId = discardedTile.id,
                        eligiblePlayerIds = eligiblePlayerIds,
                    ),
                )
            }
        }

        return Result(newState, abortiveDrawReason)
    }

    /**
     * [resolve] 的結果。
     *
     * @property tableState 套用反應資格判定後的桌況。
     * @property abortiveDrawReason 若這張捨牌依一炮多響設定判定為流局則為對應的流局原因，否則為 null。
     */
    data class Result(val tableState: TableState, val abortiveDrawReason: ExhaustiveDrawReason?)
}
