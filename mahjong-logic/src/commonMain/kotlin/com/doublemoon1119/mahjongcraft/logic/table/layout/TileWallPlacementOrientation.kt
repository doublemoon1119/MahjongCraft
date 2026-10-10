package com.doublemoon1119.mahjongcraft.logic.table.layout

/** 一張牌相對基本牌牆方向的離散朝向。 */
enum class TileWallPlacementOrientation {
    /** 保持基本牌牆格位的朝向。 */
    DEFAULT,

    /** 相對基本朝向順時針旋轉九十度。 */
    CLOCKWISE_90,

    /** 相對基本朝向旋轉一百八十度。 */
    HALF_TURN,

    /** 相對基本朝向逆時針旋轉九十度。 */
    COUNTERCLOCKWISE_90,
}
