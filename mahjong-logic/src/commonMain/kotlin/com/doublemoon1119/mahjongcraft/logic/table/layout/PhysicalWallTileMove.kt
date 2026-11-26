package com.doublemoon1119.mahjongcraft.logic.table.layout

import kotlin.uuid.Uuid

/** 一個實體牌牆 transition 階段中，單張牌的宣告式移動。 */
data class PhysicalWallTileMove(
    /** 要移動的既有牌張 Uuid。 */
    val tileId: Uuid,
    /** 此階段開始時的權威抽象來源位置。 */
    val source: TileWallPlacement,
    /** 此移動完成後的抽象目的位置。 */
    val destination: TileWallPlacement,
)
