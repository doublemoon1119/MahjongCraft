package com.doublemoon1119.mahjongcraft.logic.table

/** 規則可以處理牌牆公開牌狀態的流程節點。 */
enum class WallRevealCheckpoint {
    /** 規則要求的補牌已完成。 */
    AFTER_SUPPLEMENTAL_DRAW,

    /** 即將開始另一次規則補牌。 */
    BEFORE_SUPPLEMENTAL_DRAW,

    /** 捨牌反應已結束且無人胡牌。 */
    AFTER_DISCARD_REACTIONS,

    /** 胡牌已正式成立。 */
    WIN_CONFIRMED,
}
