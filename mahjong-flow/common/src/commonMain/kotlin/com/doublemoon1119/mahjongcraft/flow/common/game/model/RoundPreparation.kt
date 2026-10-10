package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/** 開局準備步驟要求玩家提交的受控輸入。 */
sealed interface RoundPreparationInputSpec {
    /** 只需確認的輸入。 */
    data object Confirmation : RoundPreparationInputSpec

    /** 從完整 namespaced 選項中選擇一項的輸入。 */
    data class SingleChoice(val optionIds: List<String>) : RoundPreparationInputSpec {
        init {
            require(optionIds.isNotEmpty()) { "Round preparation choices must not be empty" }
            require(optionIds.distinct().size == optionIds.size) { "Round preparation choices must be unique" }
            require(optionIds.all(NamespacedId::isValid)) {
                "Round preparation choices must use namespaced IDs"
            }
        }
    }

    /** 從指定牌集合選取限定數量的輸入。 */
    data class TileSelection(
        val eligibleTileIds: Set<Uuid>,
        val minCount: Int,
        val maxCount: Int,
    ) : RoundPreparationInputSpec {
        init {
            require(minCount >= 0) { "Minimum tile selection count must not be negative" }
            require(maxCount >= minCount) { "Maximum tile selection count must not be less than minimum" }
            require(maxCount <= eligibleTileIds.size) { "Maximum tile selection count exceeds eligible tiles" }
        }
    }
}

/** 玩家對開局準備步驟提交的受控資料。 */
sealed interface RoundPreparationSubmission {
    /** 已確認。 */
    data object Confirmed : RoundPreparationSubmission

    /** 已選擇指定選項。 */
    data class Choice(val optionId: String) : RoundPreparationSubmission

    /** 已選擇指定牌張。 */
    data class Tiles(val tileIds: Set<Uuid>) : RoundPreparationSubmission
}

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
