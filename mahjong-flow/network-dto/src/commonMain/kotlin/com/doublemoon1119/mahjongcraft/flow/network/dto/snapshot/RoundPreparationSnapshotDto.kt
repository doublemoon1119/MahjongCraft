package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RoundPreparationInputSpecDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RoundPreparationSubmissionDto
import kotlinx.serialization.Serializable

/**
 * 指定觀看者可見的開局準備網路快照。
 *
 * @property stepId 開局準備步驟識別碼。
 * @property stepIndex 開局準備步驟索引。
 * @property participantPlayerIds 需要參與此步驟的玩家 UUID 字串。
 * @property completedPlayerIds 已完成此步驟的玩家 UUID 字串。
 * @property ownInputSpec 目前 observer 的輸入規格；不適用時為 `null`。
 * @property ownSubmission 目前 observer 的提交內容；尚未提交時為 `null`。
 */
@Serializable
data class RoundPreparationSnapshotDto(
    val stepId: String,
    val stepIndex: Int,
    val participantPlayerIds: Set<String>,
    val completedPlayerIds: Set<String>,
    val ownInputSpec: RoundPreparationInputSpecDto?,
    val ownSubmission: RoundPreparationSubmissionDto?,
)

/** 將開局準備快照轉為網路資料。 */
