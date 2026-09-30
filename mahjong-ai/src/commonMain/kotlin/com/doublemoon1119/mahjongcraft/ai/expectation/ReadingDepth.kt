package com.doublemoon1119.mahjongcraft.ai.expectation

/** 對手模型從公開資訊推測對手手牌的深度。 */
enum class ReadingDepth {
    /** 只依對手不能榮和的牌、可見牌張數與副露數估計。 */
    BASIC,

    /** 另外依捨牌順序、宣告時機與副露內容推測對手的聽牌範圍與打點。 */
    ADVANCED,
}
