package com.doublemoon1119.mahjongcraft.platform.minecraft.stick

/** 麻將點棒物品的面額資料語意，不依賴物品資料的實際儲存方式。 */
object MahjongScoringStickItemData {
    /** 面額資料的欄位名稱。 */
    const val DENOMINATION_KEY: String = "denomination"

    /** 缺少或無效的面額名稱使用百分棒。 */
    fun read(storedName: String?): MahjongScoringStickDenomination = storedName?.let(MahjongScoringStickDenomination::fromNameOrDefault) ?: MahjongScoringStickDenomination.P100

    /** 以穩定名稱表示面額。 */
    fun write(denomination: MahjongScoringStickDenomination): String = denomination.name
}
