package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionStep
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTimeSource
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import kotlin.uuid.Uuid

/**
 * 將規則選擇視窗轉為持久化資料。
 *
 * @return 轉換後的結果。
 */
fun PendingRuleDecision.toPersistenceDto(): PendingRuleDecisionPersistenceDto = PendingRuleDecisionPersistenceDto(
    id = id.toString(),
    timing = timing.toPersistenceDto(),
    stepId = stepId,
    stepIndex = stepIndex,
    timeSource = step.timeSource.toPersistenceDto(),
    inputSpecsByPlayerId = inputSpecsByPlayerId.mapKeys { it.key.toString() }.mapValues { it.value.toPersistenceDto() },
    submissionsByPlayerId = submissionsByPlayerId.mapKeys { it.key.toString() }.mapValues { it.value.toPersistenceDto() },
)

/**
 * 將持久化資料還原為規則選擇視窗。
 *
 * @return 轉換後的結果。
 */
fun PendingRuleDecisionPersistenceDto.toDomain(): PendingRuleDecision = PendingRuleDecision(
    id = Uuid.parse(id),
    timing = timing.toDomain(),
    step = RuleDecisionStep(
        stepId = stepId,
        stepIndex = stepIndex,
        inputSpecsByPlayerId = inputSpecsByPlayerId.mapKeys { Uuid.parse(it.key) }.mapValues { it.value.toDomain() },
        timeSource = timeSource.toDomain(),
    ),
    submissionsByPlayerId = submissionsByPlayerId.mapKeys { Uuid.parse(it.key) }.mapValues { it.value.toDomain() },
)

/**
 * 將規則選擇視窗的時間點轉為持久化資料。
 *
 * @return 同名的持久化時間點。
 */
fun RuleDecisionTiming.toPersistenceDto(): RuleDecisionTimingPersistenceDto = RuleDecisionTimingPersistenceDto.valueOf(name)

/**
 * 將持久化的時間點還原為規則選擇視窗的時間點。
 *
 * @return 同名的時間點。
 */
fun RuleDecisionTimingPersistenceDto.toDomain(): RuleDecisionTiming = RuleDecisionTiming.valueOf(name)

/** 將規則選擇的思考時間來源轉成持久化資料。 */
private fun RuleDecisionTimeSource.toPersistenceDto(): RuleDecisionTimeSourcePersistenceDto = RuleDecisionTimeSourcePersistenceDto.valueOf(name)

/** 將持久化資料還原成規則選擇的思考時間來源。 */
private fun RuleDecisionTimeSourcePersistenceDto.toDomain(): RuleDecisionTimeSource = RuleDecisionTimeSource.valueOf(name)

/** 將規則選擇輸入規格轉成存檔 DTO。 */
private fun RuleDecisionInputSpec.toPersistenceDto(): RuleDecisionInputSpecPersistenceDto = when (this) {
    RuleDecisionInputSpec.Confirmation -> RuleDecisionInputSpecPersistenceDto.Confirmation
    is RuleDecisionInputSpec.SingleChoice -> RuleDecisionInputSpecPersistenceDto.SingleChoice(
        optionIds = optionIds,
        previewTileIdsByOptionId = previewTileIdsByOptionId.mapValues { (_, tileIds) -> tileIds.map(Uuid::toString) },
        passOptionId = passOptionId,
    )
    is RuleDecisionInputSpec.TileSelection -> RuleDecisionInputSpecPersistenceDto.TileSelection(
        eligibleTileIds = eligibleTileIds.mapTo(mutableSetOf(), Uuid::toString),
        minCount = minCount,
        maxCount = maxCount,
    )
}

/** 將已保存的規則選擇輸入規格還原。 */
private fun RuleDecisionInputSpecPersistenceDto.toDomain(): RuleDecisionInputSpec = when (this) {
    RuleDecisionInputSpecPersistenceDto.Confirmation -> RuleDecisionInputSpec.Confirmation
    is RuleDecisionInputSpecPersistenceDto.SingleChoice -> RuleDecisionInputSpec.SingleChoice(
        optionIds = optionIds,
        previewTileIdsByOptionId = previewTileIdsByOptionId.mapValues { (_, tileIds) -> tileIds.map(Uuid::parse) },
        passOptionId = passOptionId,
    )
    is RuleDecisionInputSpecPersistenceDto.TileSelection -> RuleDecisionInputSpec.TileSelection(
        eligibleTileIds = eligibleTileIds.mapTo(mutableSetOf(), Uuid::parse),
        minCount = minCount,
        maxCount = maxCount,
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
