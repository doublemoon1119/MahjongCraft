package com.doublemoon1119.mahjongcraft.platform.minecraft.stick

/** 麻將點棒物品的面額資料語意，不依賴物品資料的實際儲存方式。 */
object MahjongScoringStickItemData {
    /** 面額資料的欄位名稱。 */
    const val DENOMINATION_KEY: String = "denomination"

    /**
     * 讀取面額名稱；缺少或無效時使用百分棒。
     *
     * @param storedName 儲存的面額名稱。
     * @return 解析後的面額。
     */
    fun read(storedName: String?): MahjongScoringStickDenomination = storedName?.let(MahjongScoringStickDenomination::fromNameOrDefault) ?: MahjongScoringStickDenomination.P100

    /**
     * 以穩定名稱表示面額。
     *
     * @param denomination 要儲存的面額。
     * @return 面額 enum 名稱。
     */
    fun write(denomination: MahjongScoringStickDenomination): String = denomination.name
}
