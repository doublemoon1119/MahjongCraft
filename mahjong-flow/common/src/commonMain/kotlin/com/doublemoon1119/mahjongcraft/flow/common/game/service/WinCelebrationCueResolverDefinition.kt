package com.doublemoon1119.mahjongcraft.flow.common.game.service

/**
 * 完整規則模組 ID 與胡牌演出提示解析器的註冊定義。
 *
 * @property ruleModuleId 規則模組識別碼。
 * @property resolver 對應規則的解析器。
 */
data class WinCelebrationCueResolverDefinition(
    val ruleModuleId: String,
    val resolver: WinCelebrationCueResolver,
)
