package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 規則文字參數 ID 對應的翻譯定義。
 *
 * @property id 訊息的完整 namespaced ID。
 * @property translationKey 顯示該訊息時使用的翻譯 key。
 */
data class RoundInfoMessageDefinition(
    val id: String,
    val translationKey: String,
)
