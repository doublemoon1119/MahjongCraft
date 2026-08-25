package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 套用榮和結算：更新贏家與放銃者（或包牌責任者）的分數、記錄各贏家的 [GameAction.Ron] 動作歷史、
 * 清除反應視窗。手牌到此結束，不套用任何副露、不標記捨牌已被鳴走、不推進 `currentPlayerIndex`、
 * 不清除一發（一發是否失效在手牌已結束的情況下沒有意義）——這些皆與碰/吃/槓的結算路徑不同。
 *
 * 從 [RespondToDiscardUseCase] 抽出的共用邏輯：一般捨牌榮和與搶槓榮和（[RespondToKanUseCase]）
 * 的結算方式完全相同，差異只在「誰扮演放銃者角色」（一般捨牌是真正的放銃者；搶槓則是暗槓/加槓的
 * 宣告者）與是否要額外計入搶槓役種，這兩者都已經是規則無關的參數，不需要各自重寫一份。
 *
 * [winnerIds] 可能不只一人（一炮多響、且規則設定為多家和時）：每位贏家各自呼叫
 * [MahjongRuleModule.declareRon] 獨立結算，再把所有結算金額加總到同一份分數異動——同一位玩家
 * 有可能同時是某人的贏家、又是另一人的包牌責任者，需要正確疊加而非互相覆蓋。
 *
 * 場上供託（如立直棒）由其中一位贏家收下：只有單一贏家時就是那位贏家；多家和時依頭跳順位
 * （[TableState.nearestPlayerInTurnOrder]，以 [discarderId] 為起點）由離放銃者最近的贏家收下，
 * 不是所有贏家均分。
 */
internal object RonSettlementResolver {
    /** 榮和後桌況與每位贏家的完整算役結果。 */
    data class Result(val tableState: TableState, val resolutions: Map<Uuid, WinResolutionResult>)

    /**
     * @param state 目前的桌況（尚未套用本次榮和結算）。
     * @param players 目前的玩家列表（可能已套用本次回應者的過水/振聽等變化，尚未套用榮和結算）。
     * @param discarderId 放銃者 Uuid（一般捨牌榮和為真正的放銃者；搶槓榮和為暗槓/加槓的宣告者）。
     * @param winningTile 被榮和的那張牌。
     * @param module 該對局採用的規則模組。
     * @param winnerIds 本次榮和的贏家 Uuid 集合。
     * @param isRobbingKan 這次榮和是否為搶槓（搶加槓），透傳給 [MahjongRuleModule.declareRon]。
     * @return 套用榮和結算後的新 [TableState]（`pendingReaction`/`pendingKanReaction` 皆不動，由呼叫端
     *         視回傳值清除）；理論上不會發生的防呆情況（`declareRon` 回傳 null）則回傳 null，
     *         代表呼叫端應維持反應視窗不變、不清除。
     */
    fun resolve(
        state: TableState,
        players: List<MahjongPlayer>,
        discarderId: Uuid,
        winningTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
        winnerIds: Set<Uuid>,
        isRobbingKan: Boolean = false,
    ): Result? {
        val settlements = winnerIds.associateWith { winnerId ->
            val winner = players.first { it.id == winnerId }
            module.declareRon(state, winner, winningTile, discarderId, isRobbingKan)
        }

        // 理論上不會發生：能走到這裡代表呼叫端已經用 getLegalActions 重新驗證過每筆 Ron 目前合法，
        // 僅作防呆。
        if (settlements.values.any { it == null }) {
            return null
        }

        val scoreDeltas = mutableMapOf<Uuid, Int>()
        settlements.forEach { (winnerId, settlement) ->
            checkNotNull(settlement)
            scoreDeltas[winnerId] = (scoreDeltas[winnerId] ?: 0) + settlement.totalGained
            settlement.paymentsByPlayerId.forEach { (payerId, amount) ->
                scoreDeltas[payerId] = (scoreDeltas[payerId] ?: 0) - amount
            }
        }

        // 場上供託由其中一位贏家收下：單一贏家時就是那位贏家，多家和時依頭跳順位由離放銃者最近的
        // 贏家收下，不支援此機制的規則回傳 null
        val stickPot = module.collectStickPot(state)
        if (stickPot != null) {
            val collectorId = if (winnerIds.size == 1) {
                winnerIds.first()
            } else {
                state.nearestPlayerInTurnOrder(discarderId, winnerIds)
            }
            scoreDeltas[collectorId] = (scoreDeltas[collectorId] ?: 0) + stickPot.second
        }

        val updatedPlayers = players.map { p ->
            val updated = p.copy(score = p.score + (scoreDeltas[p.id] ?: 0))
            if (p.id in winnerIds) updated.recordAction(GameAction.Ron(winningTile.id)) else updated
        }

        return Result(
            tableState = state.copy(
                players = updatedPlayers,
                dynamicRuleState = stickPot?.first ?: state.dynamicRuleState,
            ),
            resolutions = settlements.mapValues { checkNotNull(it.value) },
        )
    }
}
