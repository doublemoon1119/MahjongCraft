package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

/**
 * 將規則選擇步驟 ID 綁定到顯示名稱的翻譯 key。
 *
 * @property id 登記項目的穩定識別碼。
 * @property translationKey 對應顯示文字的翻譯 key。
 */
data class RuleDecisionDisplayNameDefinition(
    val id: String,
    val translationKey: String,
)
