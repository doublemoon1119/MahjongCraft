package com.doublemoon1119.mahjongcraft.flow.common.game.model

/** 流局結算時規則要求如何公開一位玩家的手牌。 */
enum class ExhaustiveDrawHandDisclosure {
    /** 公開聽牌手牌，並公開規則算出的等待牌。 */
    TENPAI,

    /** 公開手牌作為流局宣告成立的證明，不公開等待牌。 */
    PROOF,

    /** 不公開手牌。 */
    CONCEALED,
}
