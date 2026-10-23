package com.doublemoon1119.mahjongcraft.flow.network.dto.model
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import kotlin.uuid.Uuid

/**
 * 將領域資料轉換為網路 DTO。
 *
 * @return 轉換後的資料。
 */
fun RuleDecisionInputSpec.toDto(): RuleDecisionInputSpecDto = when (this) {
    RuleDecisionInputSpec.Confirmation -> RuleDecisionInputSpecDto.Confirmation
    is RuleDecisionInputSpec.SingleChoice -> RuleDecisionInputSpecDto.SingleChoice(optionIds)
    is RuleDecisionInputSpec.TileSelection -> RuleDecisionInputSpecDto.TileSelection(
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
fun RuleDecisionInputSpecDto.toDomain(): RuleDecisionInputSpec = when (this) {
    RuleDecisionInputSpecDto.Confirmation -> RuleDecisionInputSpec.Confirmation
    is RuleDecisionInputSpecDto.SingleChoice -> RuleDecisionInputSpec.SingleChoice(optionIds)
    is RuleDecisionInputSpecDto.TileSelection -> RuleDecisionInputSpec.TileSelection(
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
fun RuleDecisionSubmission.toDto(): RuleDecisionSubmissionDto = when (this) {
    RuleDecisionSubmission.Confirmed -> RuleDecisionSubmissionDto.Confirmed
    is RuleDecisionSubmission.Choice -> RuleDecisionSubmissionDto.Choice(optionId)
    is RuleDecisionSubmission.Tiles -> RuleDecisionSubmissionDto.Tiles(tileIds.mapTo(mutableSetOf(), Uuid::toString))
}

/**
 * 將網路資料還原為提交。
 *
 * @return 轉換後的資料。
 */
fun RuleDecisionSubmissionDto.toDomain(): RuleDecisionSubmission = when (this) {
    RuleDecisionSubmissionDto.Confirmed -> RuleDecisionSubmission.Confirmed
    is RuleDecisionSubmissionDto.Choice -> RuleDecisionSubmission.Choice(optionId)
    is RuleDecisionSubmissionDto.Tiles -> RuleDecisionSubmission.Tiles(tileIds.mapTo(mutableSetOf(), Uuid::parse))
}
