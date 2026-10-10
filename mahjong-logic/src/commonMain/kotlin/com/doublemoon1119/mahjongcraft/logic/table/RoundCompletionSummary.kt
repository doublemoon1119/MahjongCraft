package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/**
 * 一局完成後供整場 progression 判定使用的權威摘要。
 *
 * @property outcomeId 完整 namespaced outcome ID。
 * @property classification 規則中立結果分類。
 * @property beneficiaryPlayerIds 此結果的得利者。
 * @property responsiblePlayerIds 此結果的責任玩家。
 * @property transitionDirective 莊家應連莊或過莊的明確決策。
 * @property settledScoresByPlayerId 本局結算後每位玩家的權威分數。
 */
data class RoundCompletionSummary(
    val outcomeId: String,
    val classification: RoundCompletionClassification,
    val beneficiaryPlayerIds: Set<Uuid>,
    val responsiblePlayerIds: Set<Uuid> = emptySet(),
    val transitionDirective: RoundTransitionDirective,
    val settledScoresByPlayerId: Map<Uuid, Int>,
) {
    init {
        NamespacedId.requireValid(outcomeId) { "Round completion outcomeId must be a full namespaced id: $outcomeId" }
        require(beneficiaryPlayerIds.all { it in settledScoresByPlayerId }) { "Round completion beneficiaries must belong to the table" }
        require(responsiblePlayerIds.all { it in settledScoresByPlayerId }) { "Round completion responsible players must belong to the table" }
    }
}
