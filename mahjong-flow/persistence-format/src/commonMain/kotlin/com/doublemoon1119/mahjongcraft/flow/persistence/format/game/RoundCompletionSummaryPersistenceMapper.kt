package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import kotlin.uuid.Uuid

/**
 * 將一局完成摘要轉成 persistence DTO。
 *
 * @return 轉換後的結果。
 */
fun RoundCompletionSummary.toPersistenceDto(): RoundCompletionSummaryPersistenceDto = RoundCompletionSummaryPersistenceDto(
    outcomeId = outcomeId,
    classification = RoundCompletionClassificationPersistenceDto.valueOf(classification.name),
    beneficiaryPlayerIds = beneficiaryPlayerIds.map(Uuid::toString).toSet(),
    responsiblePlayerIds = responsiblePlayerIds.map(Uuid::toString).toSet(),
    transitionDirective = transitionDirective.toPersistenceDto(),
    settledScoresByPlayerId = settledScoresByPlayerId.mapKeys { (playerId, _) -> playerId.toString() },
)

/**
 * 將 persistence DTO 還原成一局完成摘要。
 *
 * @return 轉換後的結果。
 */
fun RoundCompletionSummaryPersistenceDto.toDomain(): RoundCompletionSummary = RoundCompletionSummary(
    outcomeId = outcomeId,
    classification = RoundCompletionClassification.valueOf(classification.name),
    beneficiaryPlayerIds = beneficiaryPlayerIds.map(Uuid::parse).toSet(),
    responsiblePlayerIds = responsiblePlayerIds.map(Uuid::parse).toSet(),
    transitionDirective = transitionDirective.toDomain(),
    settledScoresByPlayerId = settledScoresByPlayerId.mapKeys { (playerId, _) -> Uuid.parse(playerId) },
)
