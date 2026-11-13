package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 牌河的擺法。 */
enum class TableDiscardArea {
    /** 每家在自己面前排成幾排，依打出順序排列。 */
    ROWS,

    /**
     * 散放在桌子中央：每一局用一張事先排好、互不重疊的位置表，打出的牌依全桌打牌順序挑空位，優先落在打牌者那一側；
     * 角度不受打牌者方向限制。位置由比它先打出的牌與牌本身決定，之後打牌或鳴牌都不會讓它移動。
     */
    SCATTERED,
}
