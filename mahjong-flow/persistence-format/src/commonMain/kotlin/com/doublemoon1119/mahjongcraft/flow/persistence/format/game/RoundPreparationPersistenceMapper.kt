package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import kotlin.uuid.Uuid

/**
 * 將開局準備步驟轉為持久化資料。
 *
 * @return 轉換後的結果。
 */
fun PendingRoundPreparation.toPersistenceDto(): PendingRoundPreparationPersistenceDto = PendingRoundPreparationPersistenceDto(
    stepId = stepId,
    stepIndex = stepIndex,
    inputSpecsByPlayerId = inputSpecsByPlayerId.mapKeys { it.key.toString() }.mapValues { it.value.toPersistenceDto() },
    submissionsByPlayerId = submissionsByPlayerId.mapKeys { it.key.toString() }.mapValues { it.value.toPersistenceDto() },
)

/**
 * 將持久化資料還原為開局準備步驟。
 *
 * @return 轉換後的結果。
 */
fun PendingRoundPreparationPersistenceDto.toDomain(): PendingRoundPreparation = PendingRoundPreparation(
    stepId = stepId,
    stepIndex = stepIndex,
    inputSpecsByPlayerId = inputSpecsByPlayerId.mapKeys { Uuid.parse(it.key) }.mapValues { it.value.toDomain() },
    submissionsByPlayerId = submissionsByPlayerId.mapKeys { Uuid.parse(it.key) }.mapValues { it.value.toDomain() },
)

private fun RoundPreparationInputSpec.toPersistenceDto(): RoundPreparationInputSpecPersistenceDto = when (this) {
    RoundPreparationInputSpec.Confirmation -> RoundPreparationInputSpecPersistenceDto.Confirmation
    is RoundPreparationInputSpec.SingleChoice -> RoundPreparationInputSpecPersistenceDto.SingleChoice(optionIds)
    is RoundPreparationInputSpec.TileSelection -> RoundPreparationInputSpecPersistenceDto.TileSelection(
        eligibleTileIds.mapTo(mutableSetOf(), Uuid::toString),
        minCount,
        maxCount,
    )
}

private fun RoundPreparationInputSpecPersistenceDto.toDomain(): RoundPreparationInputSpec = when (this) {
    RoundPreparationInputSpecPersistenceDto.Confirmation -> RoundPreparationInputSpec.Confirmation
    is RoundPreparationInputSpecPersistenceDto.SingleChoice -> RoundPreparationInputSpec.SingleChoice(optionIds)
    is RoundPreparationInputSpecPersistenceDto.TileSelection -> RoundPreparationInputSpec.TileSelection(
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
fun RoundPreparationSubmission.toPersistenceDto(): RoundPreparationSubmissionPersistenceDto = when (this) {
    RoundPreparationSubmission.Confirmed -> RoundPreparationSubmissionPersistenceDto.Confirmed
    is RoundPreparationSubmission.Choice -> RoundPreparationSubmissionPersistenceDto.Choice(optionId)
    is RoundPreparationSubmission.Tiles -> RoundPreparationSubmissionPersistenceDto.Tiles(tileIds.mapTo(mutableSetOf(), Uuid::toString))
}

/**
 * 將持久化資料還原為提交。
 *
 * @return 轉換後的結果。
 */
fun RoundPreparationSubmissionPersistenceDto.toDomain(): RoundPreparationSubmission = when (this) {
    RoundPreparationSubmissionPersistenceDto.Confirmed -> RoundPreparationSubmission.Confirmed
    is RoundPreparationSubmissionPersistenceDto.Choice -> RoundPreparationSubmission.Choice(optionId)
    is RoundPreparationSubmissionPersistenceDto.Tiles -> RoundPreparationSubmission.Tiles(tileIds.mapTo(mutableSetOf(), Uuid::parse))
}
