package com.doublemoon1119.mahjongcraft.logic.tile.component

/** 牌張目前所在的區域，不包含被取走牌的歷史參照。 */
enum class TileRegion {
    /** 一般摸牌序列。 */
    WALL,

    /** 規則另行決定補取方式的保留牌。 */
    RESERVED,

    /** 玩家已整理在手中的牌。 */
    HAND,

    /** 玩家剛摸入、尚未併入手牌的牌。 */
    DRAWN,

    /** 成立的副露或暗槓。 */
    MELD,

    /** 尚未被取走的捨牌。 */
    DISCARD,

    /** 規則從手牌移出的牌。 */
    SET_ASIDE,
}
