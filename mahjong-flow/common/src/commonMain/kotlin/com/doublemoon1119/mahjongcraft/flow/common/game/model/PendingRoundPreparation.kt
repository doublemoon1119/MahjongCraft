package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/** 一個仍在等待玩家或規則完成的開局準備步驟。 */
data class PendingRoundPreparation(
    val stepId: String,
    val stepIndex: Int,
    val inputSpecsByPlayerId: Map<Uuid, RoundPreparationInputSpec>,
    val submissionsByPlayerId: Map<Uuid, RoundPreparationSubmission> = emptyMap(),
) {
    init {
        NamespacedId.requireValid(stepId) {
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
