package com.doublemoon1119.mahjongcraft.flow.common.game.model

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
            require(optionIds.all { ':' in it && it.substringAfter(':').isNotBlank() }) {
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

/** 一個仍在等待玩家或規則完成的開局準備步驟。 */
data class PendingRoundPreparation(
    val stepId: String,
    val stepIndex: Int,
    val inputSpecsByPlayerId: Map<Uuid, RoundPreparationInputSpec>,
    val submissionsByPlayerId: Map<Uuid, RoundPreparationSubmission> = emptyMap(),
) {
    init {
        require(stepId.contains(':') && stepId.substringAfter(':').isNotBlank()) {
            "Round preparation step ID must be namespaced: $stepId"
        }
        require(stepIndex >= 0) { "Round preparation step index must not be negative" }
        require(submissionsByPlayerId.keys.all { it in inputSpecsByPlayerId }) {
            "Round preparation submissions must belong to participants"
        }
        require(
            submissionsByPlayerId.all { (playerId, submission) ->
                inputSpecsByPlayerId.getValue(playerId).accepts(submission)
            },
        ) {
            "Round preparation submissions must match participant input specs"
        }
    }

    /** 參與此步驟的玩家。 */
    val participantPlayerIds: Set<Uuid> get() = inputSpecsByPlayerId.keys

    /** 已完成提交的玩家。 */
    val completedPlayerIds: Set<Uuid> get() = submissionsByPlayerId.keys

    /** 是否已收齊所有參與者的提交。 */
    val isComplete: Boolean get() = completedPlayerIds == participantPlayerIds
}

/** 指定觀看者可見的開局準備狀態。 */
data class RoundPreparationSnapshot(
    val stepId: String,
    val stepIndex: Int,
    val participantPlayerIds: Set<Uuid>,
    val completedPlayerIds: Set<Uuid>,
    val ownInputSpec: RoundPreparationInputSpec?,
    val ownSubmission: RoundPreparationSubmission?,
)

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
