package com.doublemoon1119.mahjongcraft.flow.common.game.model

/**
 * 指定觀看者可見的規則選擇狀態。
 *
 * 只包含觀看者自己的部分；其他玩家是否被詢問、是否已提交都不在其中，與反應視窗相同。
 *
 * @property stepId 目前選擇步驟的識別碼。
 * @property stepIndex 目前選擇步驟在流程中的索引。
 * @property ownInputSpec 觀看者自己的輸入規格；非參與者時為 null。
 * @property ownSubmission 觀看者自己的提交內容；尚未提交或非參與者時為 null。
 */
data class RuleDecisionSnapshot(
    val stepId: String,
    val stepIndex: Int,
    val ownInputSpec: RuleDecisionInputSpec?,
    val ownSubmission: RuleDecisionSubmission?,
)
