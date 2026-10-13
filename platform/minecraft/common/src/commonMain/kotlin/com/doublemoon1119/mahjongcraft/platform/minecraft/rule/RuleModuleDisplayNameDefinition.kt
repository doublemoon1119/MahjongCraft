package com.doublemoon1119.mahjongcraft.platform.minecraft.rule

/**
 * 將規則模組 ID 綁定到顯示名稱的翻譯 key。
 *
 * @property ruleModuleId 適用規則模組的穩定識別碼。
 * @property translationKey 對應顯示文字的翻譯 key。
 */
data class RuleModuleDisplayNameDefinition(
    val ruleModuleId: String,
    val translationKey: String,
)
