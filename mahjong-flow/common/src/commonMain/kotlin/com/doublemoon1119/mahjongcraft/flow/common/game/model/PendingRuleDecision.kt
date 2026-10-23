package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.uuid.Uuid

/**
 * 一個仍在等待玩家或規則完成的規則選擇步驟。
 *
 * @property stepId 此選擇步驟的完整 namespaced 識別碼。
 * @property stepIndex 此步驟在選擇流程中的零起算索引。
 * @property inputSpecsByPlayerId 各參與玩家可提交的輸入規格。
 * @property submissionsByPlayerId 各玩家已提交且符合輸入規格的內容。
 */
data class PendingRuleDecision(
    val stepId: String,
    val stepIndex: Int,
    val inputSpecsByPlayerId: Map<Uuid, RuleDecisionInputSpec>,
    val submissionsByPlayerId: Map<Uuid, RuleDecisionSubmission> = emptyMap(),
) {
    init {
        NamespacedId.requireValid(stepId) {
            "Rule decision step ID must be namespaced: $stepId"
        }
        require(stepIndex >= 0) { "Rule decision step index must not be negative" }
        require(submissionsByPlayerId.keys.all { it in inputSpecsByPlayerId }) {
            "Rule decision submissions must belong to participants"
        }
        require(
            submissionsByPlayerId.all { (playerId, submission) ->
                inputSpecsByPlayerId.getValue(playerId).accepts(submission)
            },
        ) {
            "Rule decision submissions must match participant input specs"
        }
    }

    /** 參與此步驟的玩家。 */
    val participantPlayerIds: Set<Uuid> get() = inputSpecsByPlayerId.keys

    /** 已完成提交的玩家。 */
    val completedPlayerIds: Set<Uuid> get() = submissionsByPlayerId.keys

    /** 是否已收齊所有參與者的提交。 */
    val isComplete: Boolean get() = completedPlayerIds == participantPlayerIds
}
