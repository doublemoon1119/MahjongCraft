package com.doublemoon1119.mahjongcraft.flow.network.dto.model

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** 開局準備輸入規格的網路資料。 */
@Serializable
sealed interface RoundPreparationInputSpecDto {
    /** 確認輸入。 */
    @Serializable
    @SerialName("confirmation")
    data object Confirmation : RoundPreparationInputSpecDto

    /** 單選輸入。 */
    @Serializable
    @SerialName("single_choice")
    data class SingleChoice(val optionIds: List<String>) : RoundPreparationInputSpecDto

    /** 牌張選擇輸入。 */
    @Serializable
    @SerialName("tile_selection")
    data class TileSelection(
        val eligibleTileIds: Set<String>,
        val minCount: Int,
        val maxCount: Int,
    ) : RoundPreparationInputSpecDto
}

/** 開局準備提交的網路資料。 */
@Serializable
sealed interface RoundPreparationSubmissionDto {
    /** 已確認。 */
    @Serializable
    @SerialName("confirmed")
    data object Confirmed : RoundPreparationSubmissionDto

    /** 單選結果。 */
    @Serializable
    @SerialName("choice")
    data class Choice(val optionId: String) : RoundPreparationSubmissionDto

    /** 牌張選擇結果。 */
    @Serializable
    @SerialName("tiles")
    data class Tiles(val tileIds: Set<String>) : RoundPreparationSubmissionDto
}

/** 將輸入規格轉為網路資料。 */
fun RoundPreparationInputSpec.toDto(): RoundPreparationInputSpecDto = when (this) {
    RoundPreparationInputSpec.Confirmation -> RoundPreparationInputSpecDto.Confirmation
    is RoundPreparationInputSpec.SingleChoice -> RoundPreparationInputSpecDto.SingleChoice(optionIds)
    is RoundPreparationInputSpec.TileSelection -> RoundPreparationInputSpecDto.TileSelection(
        eligibleTileIds.mapTo(mutableSetOf(), Uuid::toString),
        minCount,
        maxCount,
    )
}

/** 將網路資料還原為輸入規格。 */
fun RoundPreparationInputSpecDto.toDomain(): RoundPreparationInputSpec = when (this) {
    RoundPreparationInputSpecDto.Confirmation -> RoundPreparationInputSpec.Confirmation
    is RoundPreparationInputSpecDto.SingleChoice -> RoundPreparationInputSpec.SingleChoice(optionIds)
    is RoundPreparationInputSpecDto.TileSelection -> RoundPreparationInputSpec.TileSelection(
        eligibleTileIds.mapTo(mutableSetOf(), Uuid::parse),
        minCount,
        maxCount,
    )
}

/** 將提交轉為網路資料。 */
fun RoundPreparationSubmission.toDto(): RoundPreparationSubmissionDto = when (this) {
    RoundPreparationSubmission.Confirmed -> RoundPreparationSubmissionDto.Confirmed
    is RoundPreparationSubmission.Choice -> RoundPreparationSubmissionDto.Choice(optionId)
    is RoundPreparationSubmission.Tiles -> RoundPreparationSubmissionDto.Tiles(tileIds.mapTo(mutableSetOf(), Uuid::toString))
}

/** 將網路資料還原為提交。 */
fun RoundPreparationSubmissionDto.toDomain(): RoundPreparationSubmission = when (this) {
    RoundPreparationSubmissionDto.Confirmed -> RoundPreparationSubmission.Confirmed
    is RoundPreparationSubmissionDto.Choice -> RoundPreparationSubmission.Choice(optionId)
    is RoundPreparationSubmissionDto.Tiles -> RoundPreparationSubmission.Tiles(tileIds.mapTo(mutableSetOf(), Uuid::parse))
}
