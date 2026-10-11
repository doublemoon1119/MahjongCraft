package com.doublemoon1119.mahjongcraft.flow.network.dto.model
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import kotlin.uuid.Uuid

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun RoundPreparationInputSpec.toDto(): RoundPreparationInputSpecDto = when (this) {
    RoundPreparationInputSpec.Confirmation -> RoundPreparationInputSpecDto.Confirmation
    is RoundPreparationInputSpec.SingleChoice -> RoundPreparationInputSpecDto.SingleChoice(optionIds)
    is RoundPreparationInputSpec.TileSelection -> RoundPreparationInputSpecDto.TileSelection(
        eligibleTileIds.mapTo(mutableSetOf(), Uuid::toString),
        minCount,
        maxCount,
    )
}

/**
 * 將網路資料還原為輸入規格。
 *
 * @return 轉換後的資料。
 */
fun RoundPreparationInputSpecDto.toDomain(): RoundPreparationInputSpec = when (this) {
    RoundPreparationInputSpecDto.Confirmation -> RoundPreparationInputSpec.Confirmation
    is RoundPreparationInputSpecDto.SingleChoice -> RoundPreparationInputSpec.SingleChoice(optionIds)
    is RoundPreparationInputSpecDto.TileSelection -> RoundPreparationInputSpec.TileSelection(
        eligibleTileIds.mapTo(mutableSetOf(), Uuid::parse),
        minCount,
        maxCount,
    )
}

/**
 * 將提交轉為網路資料。
 *
 * @return 轉換後的資料。
 */
fun RoundPreparationSubmission.toDto(): RoundPreparationSubmissionDto = when (this) {
    RoundPreparationSubmission.Confirmed -> RoundPreparationSubmissionDto.Confirmed
    is RoundPreparationSubmission.Choice -> RoundPreparationSubmissionDto.Choice(optionId)
    is RoundPreparationSubmission.Tiles -> RoundPreparationSubmissionDto.Tiles(tileIds.mapTo(mutableSetOf(), Uuid::toString))
}

/**
 * 將網路資料還原為提交。
 *
 * @return 轉換後的資料。
 */
fun RoundPreparationSubmissionDto.toDomain(): RoundPreparationSubmission = when (this) {
    RoundPreparationSubmissionDto.Confirmed -> RoundPreparationSubmission.Confirmed
    is RoundPreparationSubmissionDto.Choice -> RoundPreparationSubmission.Choice(optionId)
    is RoundPreparationSubmissionDto.Tiles -> RoundPreparationSubmission.Tiles(tileIds.mapTo(mutableSetOf(), Uuid::parse))
}
