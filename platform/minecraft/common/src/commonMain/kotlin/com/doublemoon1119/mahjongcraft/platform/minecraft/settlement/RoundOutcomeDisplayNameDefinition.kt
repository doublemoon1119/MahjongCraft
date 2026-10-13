package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 將特殊結算結果 ID 綁定到玩家可讀的名稱。
 *
 * @property outcomeId 結算結果的穩定識別碼。
 * @property translationKey 對應顯示文字的翻譯 key。
 */
data class RoundOutcomeDisplayNameDefinition(
    val outcomeId: String,
    val translationKey: String,
)
