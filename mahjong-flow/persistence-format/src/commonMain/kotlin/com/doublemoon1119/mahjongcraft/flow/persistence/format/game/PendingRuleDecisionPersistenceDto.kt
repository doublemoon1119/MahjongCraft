package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/**
 * 尚待完成的規則選擇視窗持久化資料。
 *
 * @property id 視窗身分的 UUID 字串。
 * @property timing 視窗開啟的時間點。
 * @property stepId 選擇步驟的穩定識別碼。
 * @property stepIndex 選擇步驟在流程中的索引。
 * @property inputSpecsByPlayerId 各玩家的輸入規格，鍵為玩家 UUID 字串。
 * @property submissionsByPlayerId 各玩家已提交的內容，鍵為玩家 UUID 字串。
 */
@Serializable
data class PendingRuleDecisionPersistenceDto(
    val id: String,
    val timing: RuleDecisionTimingPersistenceDto,
    val stepId: String,
    val stepIndex: Int,
    val inputSpecsByPlayerId: Map<String, RuleDecisionInputSpecPersistenceDto>,
    val submissionsByPlayerId: Map<String, RuleDecisionSubmissionPersistenceDto>,
)
