package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** 開局準備輸入規格的持久化資料。 */
@Serializable
sealed interface RoundPreparationInputSpecPersistenceDto {
    /** 確認輸入。 */
    @Serializable
    @SerialName("confirmation")
    data object Confirmation : RoundPreparationInputSpecPersistenceDto

    /** 單選輸入。 */
    @Serializable
    @SerialName("single_choice")
    data class SingleChoice(val optionIds: List<String>) : RoundPreparationInputSpecPersistenceDto

    /** 牌張選擇輸入。 */
    @Serializable
    @SerialName("tile_selection")
    data class TileSelection(
        val eligibleTileIds: Set<String>,
        val minCount: Int,
        val maxCount: Int,
    ) : RoundPreparationInputSpecPersistenceDto
}

/** 開局準備提交的持久化資料。 */
@Serializable
sealed interface RoundPreparationSubmissionPersistenceDto {
    /** 已確認。 */
    @Serializable
    @SerialName("confirmed")
    data object Confirmed : RoundPreparationSubmissionPersistenceDto

    /** 單選結果。 */
    @Serializable
    @SerialName("choice")
    data class Choice(val optionId: String) : RoundPreparationSubmissionPersistenceDto

    /** 牌張選擇結果。 */
    @Serializable
    @SerialName("tiles")
    data class Tiles(val tileIds: Set<String>) : RoundPreparationSubmissionPersistenceDto
}

/** 尚待完成的開局準備步驟持久化資料。 */
@Serializable
data class PendingRoundPreparationPersistenceDto(
    val stepId: String,
    val stepIndex: Int,
    val inputSpecsByPlayerId: Map<String, RoundPreparationInputSpecPersistenceDto>,
    val submissionsByPlayerId: Map<String, RoundPreparationSubmissionPersistenceDto>,
)

/** 將開局準備步驟轉為持久化資料。 */
fun PendingRoundPreparation.toPersistenceDto(): PendingRoundPreparationPersistenceDto = PendingRoundPreparationPersistenceDto(
    stepId = stepId,
    stepIndex = stepIndex,
    inputSpecsByPlayerId = inputSpecsByPlayerId.mapKeys { it.key.toString() }.mapValues { it.value.toPersistenceDto() },
    submissionsByPlayerId = submissionsByPlayerId.mapKeys { it.key.toString() }.mapValues { it.value.toPersistenceDto() },
)

/** 將持久化資料還原為開局準備步驟。 */
fun PendingRoundPreparationPersistenceDto.toDomain(): PendingRoundPreparation = PendingRoundPreparation(
    stepId = stepId,
    stepIndex = stepIndex,
    inputSpecsByPlayerId = inputSpecsByPlayerId.mapKeys { Uuid.parse(it.key) }.mapValues { it.value.toDomain() },
    submissionsByPlayerId = submissionsByPlayerId.mapKeys { Uuid.parse(it.key) }.mapValues { it.value.toDomain() },
)

/** 將輸入規格轉為持久化資料。 */
private fun RoundPreparationInputSpec.toPersistenceDto(): RoundPreparationInputSpecPersistenceDto = when (this) {
    RoundPreparationInputSpec.Confirmation -> RoundPreparationInputSpecPersistenceDto.Confirmation
    is RoundPreparationInputSpec.SingleChoice -> RoundPreparationInputSpecPersistenceDto.SingleChoice(optionIds)
    is RoundPreparationInputSpec.TileSelection -> RoundPreparationInputSpecPersistenceDto.TileSelection(
        eligibleTileIds.mapTo(mutableSetOf(), Uuid::toString),
        minCount,
        maxCount,
    )
}

/** 將持久化資料還原為輸入規格。 */
private fun RoundPreparationInputSpecPersistenceDto.toDomain(): RoundPreparationInputSpec = when (this) {
    RoundPreparationInputSpecPersistenceDto.Confirmation -> RoundPreparationInputSpec.Confirmation
    is RoundPreparationInputSpecPersistenceDto.SingleChoice -> RoundPreparationInputSpec.SingleChoice(optionIds)
    is RoundPreparationInputSpecPersistenceDto.TileSelection -> RoundPreparationInputSpec.TileSelection(
        eligibleTileIds.mapTo(mutableSetOf(), Uuid::parse),
        minCount,
        maxCount,
    )
}

/** 將提交轉為持久化資料。 */
private fun RoundPreparationSubmission.toPersistenceDto(): RoundPreparationSubmissionPersistenceDto = when (this) {
    RoundPreparationSubmission.Confirmed -> RoundPreparationSubmissionPersistenceDto.Confirmed
    is RoundPreparationSubmission.Choice -> RoundPreparationSubmissionPersistenceDto.Choice(optionId)
    is RoundPreparationSubmission.Tiles -> RoundPreparationSubmissionPersistenceDto.Tiles(
        tileIds.mapTo(mutableSetOf(), Uuid::toString),
    )
}

/** 將持久化資料還原為提交。 */
private fun RoundPreparationSubmissionPersistenceDto.toDomain(): RoundPreparationSubmission = when (this) {
    RoundPreparationSubmissionPersistenceDto.Confirmed -> RoundPreparationSubmission.Confirmed
    is RoundPreparationSubmissionPersistenceDto.Choice -> RoundPreparationSubmission.Choice(optionId)
    is RoundPreparationSubmissionPersistenceDto.Tiles -> RoundPreparationSubmission.Tiles(
        tileIds.mapTo(mutableSetOf(), Uuid::parse),
    )
}
