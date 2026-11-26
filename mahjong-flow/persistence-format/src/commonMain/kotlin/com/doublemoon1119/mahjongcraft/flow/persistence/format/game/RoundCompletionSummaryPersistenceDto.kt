package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/**
 * 一局完成摘要的 persistence DTO。
 *
 * @property outcomeId 完整 namespaced 結果 ID。
 * @property classification 規則中立的結果分類。
 * @property beneficiaryPlayerIds 得利玩家 UUID 字串集合。
 * @property responsiblePlayerIds 責任玩家 UUID 字串集合。
 * @property transitionDirective 明確莊家推進決策。
 * @property settledScoresByPlayerId 結算後權威分數。
 */
@Serializable
data class RoundCompletionSummaryPersistenceDto(
    val outcomeId: String,
    val classification: RoundCompletionClassificationPersistenceDto,
    val beneficiaryPlayerIds: Set<String>,
    val responsiblePlayerIds: Set<String>,
    val transitionDirective: RoundTransitionDirectivePersistenceDto,
    val settledScoresByPlayerId: Map<String, Int>,
)
