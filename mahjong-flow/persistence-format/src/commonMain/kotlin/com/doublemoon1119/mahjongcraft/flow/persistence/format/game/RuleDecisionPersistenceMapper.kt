package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import kotlin.uuid.Uuid

/**
 * 將規則選擇步驟轉為持久化資料。
 *
 * @return 轉換後的結果。
 */
fun PendingRuleDecision.toPersistenceDto(): PendingRuleDecisionPersistenceDto = PendingRuleDecisionPersistenceDto(
    stepId = stepId,
    stepIndex = stepIndex,
    inputSpecsByPlayerId = inputSpecsByPlayerId.mapKeys { it.key.toString() }.mapValues { it.value.toPersistenceDto() },
    submissionsByPlayerId = submissionsByPlayerId.mapKeys { it.key.toString() }.mapValues { it.value.toPersistenceDto() },
)

/**
 * 將持久化資料還原為規則選擇步驟。
 *
 * @return 轉換後的結果。
 */
fun PendingRuleDecisionPersistenceDto.toDomain(): PendingRuleDecision = PendingRuleDecision(
    stepId = stepId,
    stepIndex = stepIndex,
    inputSpecsByPlayerId = inputSpecsByPlayerId.mapKeys { Uuid.parse(it.key) }.mapValues { it.value.toDomain() },
    submissionsByPlayerId = submissionsByPlayerId.mapKeys { Uuid.parse(it.key) }.mapValues { it.value.toDomain() },
)

/** 將規則選擇輸入規格轉成存檔 DTO。 */
private fun RuleDecisionInputSpec.toPersistenceDto(): RuleDecisionInputSpecPersistenceDto = when (this) {
    RuleDecisionInputSpec.Confirmation -> RuleDecisionInputSpecPersistenceDto.Confirmation
    is RuleDecisionInputSpec.SingleChoice -> RuleDecisionInputSpecPersistenceDto.SingleChoice(optionIds)
    is RuleDecisionInputSpec.TileSelection -> RuleDecisionInputSpecPersistenceDto.TileSelection(
        eligibleTileIds.mapTo(mutableSetOf(), Uuid::toString),
        minCount,
        maxCount,
    )
}

/** 將已保存的規則選擇輸入規格還原。 */
private fun RuleDecisionInputSpecPersistenceDto.toDomain(): RuleDecisionInputSpec = when (this) {
    RuleDecisionInputSpecPersistenceDto.Confirmation -> RuleDecisionInputSpec.Confirmation
    is RuleDecisionInputSpecPersistenceDto.SingleChoice -> RuleDecisionInputSpec.SingleChoice(optionIds)
    is RuleDecisionInputSpecPersistenceDto.TileSelection -> RuleDecisionInputSpec.TileSelection(
        eligibleTileIds.mapTo(mutableSetOf(), Uuid::parse),
        minCount,
        maxCount,
    )
}

/**
 * 將提交轉為持久化資料。
 *
 * @return 轉換後的結果。
 */
fun RuleDecisionSubmission.toPersistenceDto(): RuleDecisionSubmissionPersistenceDto = when (this) {
    RuleDecisionSubmission.Confirmed -> RuleDecisionSubmissionPersistenceDto.Confirmed
    is RuleDecisionSubmission.Choice -> RuleDecisionSubmissionPersistenceDto.Choice(optionId)
    is RuleDecisionSubmission.Tiles -> RuleDecisionSubmissionPersistenceDto.Tiles(tileIds.mapTo(mutableSetOf(), Uuid::toString))
}

/**
 * 將持久化資料還原為提交。
 *
 * @return 轉換後的結果。
 */
fun RuleDecisionSubmissionPersistenceDto.toDomain(): RuleDecisionSubmission = when (this) {
    RuleDecisionSubmissionPersistenceDto.Confirmed -> RuleDecisionSubmission.Confirmed
    is RuleDecisionSubmissionPersistenceDto.Choice -> RuleDecisionSubmission.Choice(optionId)
    is RuleDecisionSubmissionPersistenceDto.Tiles -> RuleDecisionSubmission.Tiles(tileIds.mapTo(mutableSetOf(), Uuid::parse))
}
