package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.logic.judgment.DiscardReadinessAnalysis

/**
 * 同一次 Flow 查詢建立的特殊動作候選與一般捨牌分析。
 *
 * @property ruleModuleId 這局採用的規則模組 ID，決定動作用語。
 * @property actions Minecraft 指令與 HUD 共用的特殊動作候選。
 * @property discardAnalyses 自己回合的一般捨牌分析；不適用時為空。
 */
data class ResolvedGameActionCandidates(
    val ruleModuleId: String,
    val actions: List<GameActionCandidate>,
    val discardAnalyses: List<DiscardReadinessAnalysis>,
)
