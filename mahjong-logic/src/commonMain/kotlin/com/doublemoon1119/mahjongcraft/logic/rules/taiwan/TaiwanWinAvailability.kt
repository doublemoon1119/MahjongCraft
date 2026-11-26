package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

/** 一張台灣麻將等待牌在目前桌況下的胡牌資格。 */
internal enum class TaiwanWinAvailability {
    /** 胡別人打出的牌或自摸都已達到起胡台數。 */
    AVAILABLE,

    /** 只有自摸增加的台數能達到起胡台數。 */
    TSUMO_ONLY,

    /** 牌型成立，但胡牌與自摸都尚未達到起胡台數。 */
    BELOW_MINIMUM,

    /** 目前桌況下不能用這張牌完成有效牌型。 */
    NOT_WINNING,
}
