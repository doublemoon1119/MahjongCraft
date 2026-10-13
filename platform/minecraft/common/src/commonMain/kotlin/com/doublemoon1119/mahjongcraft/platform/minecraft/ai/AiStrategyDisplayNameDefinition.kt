package com.doublemoon1119.mahjongcraft.platform.minecraft.ai

/**
 * 將 AI 策略 ID 綁定到顯示名稱的翻譯 key。
 *
 * @property strategyKey AI 策略的穩定識別碼。
 * @property translationKey 對應顯示文字的翻譯 key。
 */
data class AiStrategyDisplayNameDefinition(
    val strategyKey: String,
    val translationKey: String,
)
