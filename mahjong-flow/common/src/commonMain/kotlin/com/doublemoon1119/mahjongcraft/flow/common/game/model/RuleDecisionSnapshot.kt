package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/**
 * 指定觀看者可見的規則選擇狀態。
 *
 * @property stepId 目前選擇步驟的識別碼。
 * @property stepIndex 目前選擇步驟在流程中的索引。
 * @property participantPlayerIds 參與目前步驟的玩家識別碼。
 * @property completedPlayerIds 已提交目前步驟內容的玩家識別碼。
 * @property ownInputSpec 觀看者自己的輸入規格；非參與者時為 null。
 * @property ownSubmission 觀看者自己的提交內容；尚未提交或非參與者時為 null。
 */
data class RuleDecisionSnapshot(
    val stepId: String,
    val stepIndex: Int,
    val participantPlayerIds: Set<Uuid>,
    val completedPlayerIds: Set<Uuid>,
    val ownInputSpec: RuleDecisionInputSpec?,
    val ownSubmission: RuleDecisionSubmission?,
)
