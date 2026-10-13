package com.doublemoon1119.mahjongcraft.platform.minecraft.preparation

/**
 * 將開局準備步驟 ID 綁定到顯示名稱的翻譯 key。
 *
 * @property id 登記項目的穩定識別碼。
 * @property translationKey 對應顯示文字的翻譯 key。
 */
data class RoundPreparationDisplayNameDefinition(
    val id: String,
    val translationKey: String,
)
