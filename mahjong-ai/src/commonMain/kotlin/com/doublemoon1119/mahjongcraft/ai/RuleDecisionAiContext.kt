package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.common.game.model.defaultSubmission
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.uuid.Uuid

/**
 * AI 在規則選擇步驟中可見的決策資訊。
 *
 * @property snapshot 以本次提交者為觀察者的桌況快照。
 * @property selfId 本次需要提交選擇內容的 AI 玩家識別碼。
 * @property timing 這個選擇視窗開啟的時間點。
 * @property stepId 正在處理的選擇步驟識別碼。
 * @property inputSpec 本次提交允許使用的輸入選項與數量限制。
 */
data class RuleDecisionAiContext(
    val snapshot: TableStateSnapshot,
    val selfId: Uuid,
    val timing: RuleDecisionTiming,
    val stepId: String,
    val inputSpec: RuleDecisionInputSpec,
)

/**
 * 依受控輸入產生可重現的預設 AI 提交。
 *
 * @return 由 [RuleDecisionAiContext.inputSpec] 決定的預設提交；不會套用至權威桌況。
 */
fun RuleDecisionAiContext.defaultSubmission(): RuleDecisionSubmission = inputSpec.defaultSubmission()
