package com.doublemoon1119.mahjongcraft.platform.minecraft.text

/**
 * 一段可依規則換說法的共用文字，以及沒有規則覆寫時使用的中立用語。
 *
 * @property wordingId 這段文字的穩定識別碼。
 * @property translationKey 中立用語的翻譯鍵。
 */
data class RuleWordingDefaultDefinition(
    val wordingId: String,
    val translationKey: String,
)
