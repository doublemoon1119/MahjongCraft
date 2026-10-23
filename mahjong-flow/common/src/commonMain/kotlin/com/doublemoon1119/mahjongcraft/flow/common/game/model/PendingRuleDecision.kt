package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/**
 * 一個仍在等待玩家或規則完成的規則選擇視窗。
 *
 * 每次開啟視窗都會配發新的 [id]，包括同一次選擇流程的下一個步驟；提交必須帶上同一個身分，晚到、重送或上一個視窗的提交
 * 都會被拒絕。
 *
 * @property id 這個視窗的身分。
 * @property timing 視窗開啟的時間點，決定選完後牌局從哪裡接續。
 * @property step 規則要求的選擇步驟。
 * @property submissionsByPlayerId 各玩家已提交且符合輸入規格的內容。
 */
data class PendingRuleDecision(
    val id: Uuid,
    val timing: RuleDecisionTiming,
    val step: RuleDecisionStep,
    val submissionsByPlayerId: Map<Uuid, RuleDecisionSubmission> = emptyMap(),
) {
    init {
        require(submissionsByPlayerId.keys.all { it in step.inputSpecsByPlayerId }) {
            "Rule decision submissions must belong to participants"
        }
        require(
            submissionsByPlayerId.all { (playerId, submission) ->
                step.inputSpecsByPlayerId.getValue(playerId).accepts(submission)
            },
        ) {
            "Rule decision submissions must match participant input specs"
        }
    }

    /** 此步驟的完整 namespaced 識別碼。 */
    val stepId: String get() = step.stepId

    /** 此步驟在同一次選擇流程中的零起算索引。 */
    val stepIndex: Int get() = step.stepIndex

    /** 各參與玩家可提交的輸入規格。 */
    val inputSpecsByPlayerId: Map<Uuid, RuleDecisionInputSpec> get() = step.inputSpecsByPlayerId

    /** 參與此步驟的玩家。 */
    val participantPlayerIds: Set<Uuid> get() = step.participantPlayerIds

    /** 已完成提交的玩家。 */
    val completedPlayerIds: Set<Uuid> get() = submissionsByPlayerId.keys

    /** 是否已收齊所有參與者的提交。 */
    val isComplete: Boolean get() = completedPlayerIds == participantPlayerIds

    /** 開啟視窗的方式。 */
    companion object {
        /**
         * 以新的視窗身分開啟 [step]。
         *
         * @param step 規則要求的選擇步驟。
         * @param timing 視窗開啟的時間點。
         * @return 尚未有任何提交的新視窗。
         */
        fun open(step: RuleDecisionStep, timing: RuleDecisionTiming): PendingRuleDecision = PendingRuleDecision(
            id = Uuid.random(),
            timing = timing,
            step = step,
        )
    }
}
