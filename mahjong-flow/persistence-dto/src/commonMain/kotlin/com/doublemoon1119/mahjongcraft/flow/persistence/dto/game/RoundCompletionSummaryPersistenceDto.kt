package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

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

/** 一局完成結果分類的 persistence DTO。 */
@Serializable
enum class RoundCompletionClassificationPersistenceDto {
    /** 胡牌或胡牌等價結果。 */
    WIN,

    /** 一般荒牌流局。 */
    EXHAUSTIVE_DRAW,

    /** 途中流局。 */
    ABORTIVE_DRAW,

    /** 第三方規則結果。 */
    EXTENSION,
}

/** 將一局完成摘要轉成 persistence DTO。 */
fun RoundCompletionSummary.toPersistenceDto(): RoundCompletionSummaryPersistenceDto = RoundCompletionSummaryPersistenceDto(
    outcomeId = outcomeId,
    classification = RoundCompletionClassificationPersistenceDto.valueOf(classification.name),
    beneficiaryPlayerIds = beneficiaryPlayerIds.map(Uuid::toString).toSet(),
    responsiblePlayerIds = responsiblePlayerIds.map(Uuid::toString).toSet(),
    transitionDirective = transitionDirective.toPersistenceDto(),
    settledScoresByPlayerId = settledScoresByPlayerId.mapKeys { (playerId, _) -> playerId.toString() },
)

/** 將 persistence DTO 還原成一局完成摘要。 */
fun RoundCompletionSummaryPersistenceDto.toDomain(): RoundCompletionSummary = RoundCompletionSummary(
    outcomeId = outcomeId,
    classification = RoundCompletionClassification.valueOf(classification.name),
    beneficiaryPlayerIds = beneficiaryPlayerIds.map(Uuid::parse).toSet(),
    responsiblePlayerIds = responsiblePlayerIds.map(Uuid::parse).toSet(),
    transitionDirective = transitionDirective.toDomain(),
    settledScoresByPlayerId = settledScoresByPlayerId.mapKeys { (playerId, _) -> Uuid.parse(playerId) },
)
