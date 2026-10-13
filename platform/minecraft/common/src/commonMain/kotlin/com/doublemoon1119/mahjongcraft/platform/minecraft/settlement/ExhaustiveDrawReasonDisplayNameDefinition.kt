package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 將流局原因 ID 綁定到玩家可讀的名稱。
 *
 * @property reasonId 流局原因的穩定識別碼。
 * @property translationKey 對應顯示文字的翻譯 key。
 */
data class ExhaustiveDrawReasonDisplayNameDefinition(
    val reasonId: String,
    val translationKey: String,
)
