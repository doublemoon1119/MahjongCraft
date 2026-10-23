package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RuleDecisionInputSpecDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RuleDecisionSubmissionDto
import kotlinx.serialization.Serializable

/**
 * 指定觀看者可見的規則選擇網路快照。
 *
 * @property stepId 規則選擇步驟識別碼。
 * @property stepIndex 規則選擇步驟索引。
 * @property participantPlayerIds 需要參與此步驟的玩家 UUID 字串。
 * @property completedPlayerIds 已完成此步驟的玩家 UUID 字串。
 * @property ownInputSpec 目前 observer 的輸入規格；不適用時為 `null`。
 * @property ownSubmission 目前 observer 的提交內容；尚未提交時為 `null`。
 */
@Serializable
data class RuleDecisionSnapshotDto(
    val stepId: String,
    val stepIndex: Int,
    val participantPlayerIds: Set<String>,
    val completedPlayerIds: Set<String>,
    val ownInputSpec: RuleDecisionInputSpecDto?,
    val ownSubmission: RuleDecisionSubmissionDto?,
)
