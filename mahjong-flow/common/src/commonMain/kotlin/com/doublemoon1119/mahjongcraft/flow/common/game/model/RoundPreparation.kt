package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/**
 * 驗證提交內容是否符合此受控輸入的結構限制。
 *
 * @param submission 欲驗證的玩家提交內容。
 * @return 提交符合此輸入的選項與數量限制時為 `true`。
 */
fun RoundPreparationInputSpec.accepts(submission: RoundPreparationSubmission): Boolean = when (this) {
    RoundPreparationInputSpec.Confirmation -> submission == RoundPreparationSubmission.Confirmed
    is RoundPreparationInputSpec.SingleChoice -> submission is RoundPreparationSubmission.Choice && submission.optionId in optionIds
    is RoundPreparationInputSpec.TileSelection ->
        submission is RoundPreparationSubmission.Tiles &&
            submission.tileIds.size in minCount..maxCount &&
            submission.tileIds.all { it in eligibleTileIds }
}

/**
 * 依穩定順序從受控輸入產生規則中立的預設提交。
 *
 * @return 符合此輸入規格的預設提交。
 */
fun RoundPreparationInputSpec.defaultSubmission(): RoundPreparationSubmission = when (this) {
    RoundPreparationInputSpec.Confirmation -> RoundPreparationSubmission.Confirmed
    is RoundPreparationInputSpec.SingleChoice -> RoundPreparationSubmission.Choice(optionIds.first())
    is RoundPreparationInputSpec.TileSelection -> RoundPreparationSubmission.Tiles(
        eligibleTileIds.sortedBy(Uuid::toString).take(minCount).toSet(),
    )
}
