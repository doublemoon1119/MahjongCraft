package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

/**
 * 將規則選擇選項 ID 綁定到顯示名稱的翻譯 key；名稱顯示在決策面板的選項卡片上。
 *
 * @property id 登記項目的穩定識別碼。
 * @property translationKey 對應顯示文字的翻譯 key。
 */
data class RuleDecisionDisplayNameDefinition(
    val id: String,
    val translationKey: String,
)
