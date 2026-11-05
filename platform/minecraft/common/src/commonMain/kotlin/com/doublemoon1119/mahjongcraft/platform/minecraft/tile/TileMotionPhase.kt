package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 一步之中動作播放的先後段落：先離開，再讓位，最後進入；後一段等前一段的動作都完成才開始。
 */
enum class TileMotionPhase {
    /** 離開原本那一排、飛到別區的牌，例如打出的牌、移入副露或移出區的牌；直接擺放的牌也在這一段。 */
    DEPART,

    /** 留在原排的牌平移補位或讓位、以弧線插入的牌與原地翻面的牌。 */
    MAKE_ROOM,

    /** 從牌牆或保留區摸進來的牌，等原排讓好位置才落下。 */
    ARRIVE,
}
