package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/**
 * 尚待完成的開局準備步驟持久化資料。
 *
 * @property stepId 準備步驟的穩定識別碼。
 * @property stepIndex 準備步驟在流程中的索引。
 * @property inputSpecsByPlayerId 各玩家的輸入規格，鍵為玩家 UUID 字串。
 * @property submissionsByPlayerId 各玩家已提交的內容，鍵為玩家 UUID 字串。
 */
@Serializable
data class PendingRoundPreparationPersistenceDto(
    val stepId: String,
    val stepIndex: Int,
    val inputSpecsByPlayerId: Map<String, RoundPreparationInputSpecPersistenceDto>,
    val submissionsByPlayerId: Map<String, RoundPreparationSubmissionPersistenceDto>,
)
