package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.SettlementExtrasContext
import com.doublemoon1119.mahjongcraft.logic.module.SettlementExtrasOccasion
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 收下場上供託，並依規則的 [MahjongRuleModule.allocateSettlementExtras] 決定供託與連莊點數的歸屬。
 *
 * 只計算分數變化與收下供託後的動態狀態，不寫入桌況；呼叫端把結果和手牌結算合併後一次寫入。規則的分配不合法時丟出
 * [RuleContractViolationException]，由既有的中止收尾結束對局。
 */
internal object SettlementExtrasResolver {
    /**
     * 供託與連莊點數的結算結果。
     *
     * @property scoreDeltas 供託與連莊點數造成的分數變化，只含分數有變的玩家。
     * @property dynamicRuleState 收下供託後的動態狀態；規則沒有供託機制時是原本的狀態。
     * @property stickPotRecipientIds 分到供託的玩家。
     */
    data class Result(
        val scoreDeltas: Map<Uuid, Int>,
        val dynamicRuleState: DynamicRuleState?,
        val stickPotRecipientIds: Set<Uuid>,
    )

    /**
     * 收下供託並計算規則指定的分配。
     *
     * @param state 套用本次結算前的桌況。
     * @param module 對局採用的規則模組。
     * @param occasion 分配的時機。
     * @param candidatePlayerIds 可以收款的玩家，依優先順序排列。
     * @param discarderId 放銃者；沒有時為 null。
     * @param resolutions 各和牌者的手牌結算；沒有手牌結算時為空 map。
     * @return 分數變化與收下供託後的動態狀態。
     * @throws RuleContractViolationException 規則的分配或連莊點數付款不合法時。
     */
    fun resolve(
        state: TableState,
        module: MahjongRuleModule<*>,
        occasion: SettlementExtrasOccasion,
        candidatePlayerIds: List<Uuid>,
        discarderId: Uuid? = null,
        resolutions: Map<Uuid, WinResolutionResult> = emptyMap(),
    ): Result {
        val stickPot = module.collectStickPot(state)
        val stickPotAmount = stickPot?.second ?: 0
        val allocation = module.allocateSettlementExtras(
            SettlementExtrasContext(
                tableState = state,
                occasion = occasion,
                candidatePlayerIds = candidatePlayerIds,
                stickPotAmount = stickPotAmount,
            ),
        )
        val candidates = candidatePlayerIds.toSet()
        val shares = allocation.stickPotShares
        requireContract(shares.keys.all { it in candidates }) { "Stick pot shares went to a player who cannot receive them" }
        requireContract(shares.values.all { it >= 0 }) { "Stick pot shares must not be negative" }
        val sharedTotal = shares.values.sumOf { it.toLong() }
        val discardsLeftover = occasion == SettlementExtrasOccasion.MATCH_END && shares.isEmpty()
        requireContract(sharedTotal == stickPotAmount.toLong() || discardsLeftover) {
            "Stick pot shares total $sharedTotal does not match the collected $stickPotAmount"
        }
        val comboRecipients = allocation.comboBonusRecipientIds
        requireContract(comboRecipients.all { it in candidates }) { "Combo bonus went to a player who cannot receive it" }
        requireContract(occasion != SettlementExtrasOccasion.MATCH_END || comboRecipients.isEmpty()) {
            "Combo bonus cannot be paid at the end of the match"
        }

        val tablePlayerIds = state.players.mapTo(mutableSetOf()) { it.id }
        val deltas = mutableMapOf<Uuid, Long>()
        shares.forEach { (playerId, share) -> deltas[playerId] = (deltas[playerId] ?: 0L) + share }
        candidatePlayerIds.filter { it in comboRecipients }.forEach { recipientId ->
            module.resolveComboBonusPayments(state, recipientId, discarderId, resolutions[recipientId]).forEach { (payerId, amount) ->
                requireContract(payerId in tablePlayerIds && payerId != recipientId) { "Combo bonus payer $payerId is not another table player" }
                requireContract(amount >= 0) { "Combo bonus payments must not be negative" }
                deltas[recipientId] = (deltas[recipientId] ?: 0L) + amount
                deltas[payerId] = (deltas[payerId] ?: 0L) - amount
            }
        }
        requireContract(deltas.values.all { it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }) { "Settlement extras overflow a score" }

        return Result(
            scoreDeltas = deltas.filterValues { it != 0L }.mapValues { (_, delta) -> delta.toInt() },
            dynamicRuleState = stickPot?.first ?: state.dynamicRuleState,
            stickPotRecipientIds = shares.filterValues { it > 0 }.keys,
        )
    }

    /**
     * 規則的回傳違反契約時中止。
     *
     * @param condition 符合契約時為 true。
     * @param diagnostic 違反時的診斷訊息。
     * @throws RuleContractViolationException [condition] 為 false 時。
     */
    private inline fun requireContract(condition: Boolean, diagnostic: () -> String) {
        if (!condition) throw RuleContractViolationException(GameAbortReasonIds.RULE_EXECUTION_FAILED, diagnostic())
    }
}
