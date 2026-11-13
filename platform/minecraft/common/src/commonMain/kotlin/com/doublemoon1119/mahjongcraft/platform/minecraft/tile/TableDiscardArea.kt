package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 牌河的擺法。 */
enum class TableDiscardArea {
    /** 每家在自己面前排成幾排，依打出順序排列。 */
    ROWS,

    /**
     * 散放在桌子中央：每家的牌落在靠近自己那一側的範圍內，位置與角度由打出的順序與牌本身決定，不照順序排成一排；
     * 牌面以打牌者的方向為基準，只左右偏轉一點。
     */
    SCATTERED,
}
