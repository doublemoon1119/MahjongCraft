package com.doublemoon1119.mahjongcraft.logic.module

/** 分配場上供託與連莊點數的時機。 */
enum class SettlementExtrasOccasion {
    /** 和牌或與和牌等價的結果，例如自摸、榮和、搶槓或流局滿貫。 */
    WIN,

    /** 一般流局時有玩家依規則收下供託。 */
    EXHAUSTIVE_DRAW,

    /** 整場對局結束時處理桌上還沒被收下的供託；不收連莊點數。 */
    MATCH_END,
}
