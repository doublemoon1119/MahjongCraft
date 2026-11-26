package com.doublemoon1119.mahjongcraft.flow.api.event

/** 和牌方式的規則中立分類。 */
enum class WinMethod {
    /** 玩家自己摸到和牌。 */
    SELF_DRAW,

    /** 玩家榮和他人打出的牌。 */
    DISCARD,

    /** 規則沒有提供足夠公開資訊判斷方式。 */
    UNKNOWN,
}
