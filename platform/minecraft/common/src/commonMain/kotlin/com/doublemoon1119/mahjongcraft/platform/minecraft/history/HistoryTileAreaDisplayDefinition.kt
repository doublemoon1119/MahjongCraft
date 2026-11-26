package com.doublemoon1119.mahjongcraft.platform.minecraft.history

/**
 * 將玩家移出牌區的識別碼綁定到牌譜上的區域標題，例如三人日麻的北牌區顯示「拔出的牌」。
 *
 * @property areaId 規則定義的牌區完整識別碼。
 * @property translationKey 區域標題的翻譯 key。
 */
data class HistoryTileAreaDisplayDefinition(
    val areaId: String,
    val translationKey: String,
)
