package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.judgment.HandReadinessAnalysis

/** 一位參與者目前手牌的私人分析，以及解析狀態文字所需的規則模組 ID。 */
data class HandReadinessSnapshot(
    val ruleModuleId: String,
    val analysis: HandReadinessAnalysis,
)
