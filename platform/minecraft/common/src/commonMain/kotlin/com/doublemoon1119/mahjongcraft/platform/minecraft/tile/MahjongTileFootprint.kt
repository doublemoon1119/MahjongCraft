package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/** 一張牌在桌面上佔用的形狀。 */
enum class MahjongTileFootprint {
    /** 直立：沿排列方向為牌寬，垂直方向為牌厚。 */
    STANDING,

    /** 平放：沿排列方向為牌寬，垂直方向為牌高。 */
    LYING,

    /** 橫放：沿排列方向為牌高，垂直方向為牌寬。 */
    LYING_SIDEWAYS,
}
