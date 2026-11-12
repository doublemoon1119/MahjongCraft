package com.doublemoon1119.mahjongcraft.platform.minecraft.text

/**
 * 規則為一段共用文字登記自己的說法，覆寫該規則對局中的中立用語。
 *
 * @property ruleModuleId 套用這個說法的規則模組 ID。
 * @property wordingId 被覆寫的共用文字識別碼。
 * @property translationKey 這個規則使用的翻譯鍵。
 */
data class RuleWordingDefinition(
    val ruleModuleId: String,
    val wordingId: String,
    val translationKey: String,
)
