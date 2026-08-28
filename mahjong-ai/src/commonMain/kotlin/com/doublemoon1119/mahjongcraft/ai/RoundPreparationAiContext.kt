package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationInputSpec
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSubmission
import com.doublemoon1119.mahjongcraft.flow.common.game.model.defaultSubmission
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import kotlin.uuid.Uuid

/** AI 在開局準備步驟中可見的決策上下文。 */
data class RoundPreparationAiContext(
    val snapshot: TableStateSnapshot,
    val selfId: Uuid,
    val stepId: String,
    val inputSpec: RoundPreparationInputSpec,
)

/** 依受控輸入產生可重現的預設 AI 提交。 */
fun RoundPreparationAiContext.defaultSubmission(): RoundPreparationSubmission = inputSpec.defaultSubmission()
