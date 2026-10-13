package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule

/**
 * 保存規則模組與對手模型的建立函式；建立時才傳入目前規則與讀牌深度。
 *
 * @property ruleModuleId 對應規則模組的 namespaced 識別碼。
 * @property factory 依規則模組與讀牌深度建立模型的工廠。
 */
data class OpponentModelDefinition(
    val ruleModuleId: String,
    val factory: (MahjongRuleModule<*>, ReadingDepth) -> OpponentModel,
)
