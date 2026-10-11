package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/** 驗證 [submission] 是否符合此受控輸入的結構限制。 */
fun RoundPreparationInputSpec.accepts(submission: RoundPreparationSubmission): Boolean = when (this) {
    RoundPreparationInputSpec.Confirmation -> submission == RoundPreparationSubmission.Confirmed
    is RoundPreparationInputSpec.SingleChoice -> submission is RoundPreparationSubmission.Choice && submission.optionId in optionIds
    is RoundPreparationInputSpec.TileSelection ->
        submission is RoundPreparationSubmission.Tiles &&
            submission.tileIds.size in minCount..maxCount &&
            submission.tileIds.all { it in eligibleTileIds }
}

/** 依穩定順序從受控輸入產生規則中立的預設提交。 */
fun RoundPreparationInputSpec.defaultSubmission(): RoundPreparationSubmission = when (this) {
    RoundPreparationInputSpec.Confirmation -> RoundPreparationSubmission.Confirmed
    is RoundPreparationInputSpec.SingleChoice -> RoundPreparationSubmission.Choice(optionIds.first())
    is RoundPreparationInputSpec.TileSelection -> RoundPreparationSubmission.Tiles(
        eligibleTileIds.sortedBy(Uuid::toString).take(minCount).toSet(),
    )
}
