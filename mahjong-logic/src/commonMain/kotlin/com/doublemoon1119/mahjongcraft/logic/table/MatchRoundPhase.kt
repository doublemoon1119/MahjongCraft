package com.doublemoon1119.mahjongcraft.logic.table

/** 一個局位在原定賽程或延長賽中的階段。 */
enum class MatchRoundPhase {
    /** 規則原定賽程。 */
    REGULAR,

    /** 原定賽程結束後的延長賽。 */
    EXTRA,
}
