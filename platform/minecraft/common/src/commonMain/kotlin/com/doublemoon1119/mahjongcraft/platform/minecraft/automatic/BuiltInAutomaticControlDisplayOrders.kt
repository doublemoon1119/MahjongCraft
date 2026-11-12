package com.doublemoon1119.mahjongcraft.platform.minecraft.automatic

/** 內建自動操作在清單中的排列順序；規則覆寫顯示時沿用同一個順序。 */
object BuiltInAutomaticControlDisplayOrders {
    /** 自動理牌。 */
    const val AUTO_SORT_HAND: Int = 0

    /** 自動和牌。 */
    const val AUTO_WIN: Int = 10

    /** 自動跳過鳴牌。 */
    const val DECLINE_CALLS: Int = 20

    /** 自動打出摸到的牌。 */
    const val AUTO_TSUMOGIRI: Int = 30
}
