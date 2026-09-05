package com.doublemoon1119.mahjongcraft.logic.rules.riichi

/** 一張日麻等待牌在目前權威桌況下的和牌可用性。 */
enum class RiichiWinAvailability {
    /** 榮和或自摸至少一種已符合起胡限制。 */
    AVAILABLE,

    /** 目前只有自摸符合起胡限制。 */
    TSUMO_ONLY,

    /** 完成牌型目前沒有任何非寶牌役種。 */
    NO_YAKU,

    /** 已有役種，但役種番數尚未達到起胡限制。 */
    BELOW_MINIMUM,
}
