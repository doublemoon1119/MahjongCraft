package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/**
 * 提供給規則模組判定本局結束後莊家如何推進的完整結果情境。
 *
 * 規則模組應只根據這個情境決定連莊或過莊，不應從動作紀錄重新猜測本局結果。
 *
 * @property tableState 本局結算後的權威桌況。
 * @property outcomeId 本局結果的完整 namespaced 識別碼。
 * @property classification 本局結果的規則中立分類。
 * @property beneficiaryPlayerIds 本局結果的得利玩家；一般流局時可代表聽牌玩家。
 * @property responsiblePlayerIds 本局結果的責任玩家；沒有責任歸屬時為空集合。
 * @property exhaustiveDrawReason 本局是流局時使用的規則流局原因；其他結果為 null。
 */
data class RoundTransitionContext(
    val tableState: TableState,
    val outcomeId: String,
    val classification: RoundCompletionClassification,
    val beneficiaryPlayerIds: Set<Uuid>,
    val responsiblePlayerIds: Set<Uuid>,
    val exhaustiveDrawReason: ExhaustiveDrawReason? = null,
) {
    init {
        NamespacedId.requireValid(outcomeId) { "Round transition outcomeId must be a full namespaced id: $outcomeId" }
        val playerIds = tableState.players.mapTo(mutableSetOf()) { it.id }
        require(beneficiaryPlayerIds.all { it in playerIds }) {
            "Round transition beneficiaries must belong to the table"
        }
        require(responsiblePlayerIds.all { it in playerIds }) {
            "Round transition responsible players must belong to the table"
        }
        val isDraw = classification == RoundCompletionClassification.EXHAUSTIVE_DRAW ||
            classification == RoundCompletionClassification.ABORTIVE_DRAW
        require(isDraw == (exhaustiveDrawReason != null)) {
            "Exhaustive draw reason must match the round classification"
        }
    }
}
