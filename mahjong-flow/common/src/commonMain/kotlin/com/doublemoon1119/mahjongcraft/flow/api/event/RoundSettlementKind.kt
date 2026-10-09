package com.doublemoon1119.mahjongcraft.flow.api.event

/** 結算的種類。 */
enum class RoundSettlementKind {
    /** 和牌。 */
    WIN,

    /** 流局，包含途中流局。 */
    DRAW,

    /** 規則提供的特殊結果，例如日麻的流局滿貫。 */
    SPECIAL,
}
