package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 一張牌在一步之中的動作種類。 */
enum class TileMotionKind {
    /** 呈現端還沒有這張牌之前的位置，直接擺到新位置。 */
    PLACE,

    /** 從牌牆或保留區摸起：抬起、移到新位置後落下。 */
    DRAW,

    /** 以拋物線飛到另一個區域，例如捨牌、鳴牌移入副露、移出手牌。 */
    FLIGHT,

    /** 在同一排內平移，前後順序不變，不會穿過其他牌。 */
    SLIDE,

    /** 抬高後從那一排上方以弧線越過其他牌，再落到新位置。 */
    ARC,

    /** 位置不變，只改變姿態，例如翻開或倒下。 */
    TURN,
}
