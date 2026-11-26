package com.doublemoon1119.mahjongcraft.flow.api.event

/** 公開和牌群組所對應的已提交結果種類。 */
enum class WinGroupSource {
    /** 一般和牌結算。 */
    WIN_SETTLEMENT,

    /** 規則產生的特殊結果。 */
    RULE_RESULT,
}
