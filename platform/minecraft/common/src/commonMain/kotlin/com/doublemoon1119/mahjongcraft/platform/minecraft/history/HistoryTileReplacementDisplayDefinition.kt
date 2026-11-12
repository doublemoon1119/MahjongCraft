package com.doublemoon1119.mahjongcraft.platform.minecraft.history

/**
 * 將規則把移出的牌補回來的流程綁定到牌譜上的用語，例如台灣麻將的「補花」。
 *
 * @property ruleModuleId 使用這個用語的規則模組完整識別碼。
 * @property translationKey 用語的翻譯 key。
 */
data class HistoryTileReplacementDisplayDefinition(
    val ruleModuleId: String,
    val translationKey: String,
)
