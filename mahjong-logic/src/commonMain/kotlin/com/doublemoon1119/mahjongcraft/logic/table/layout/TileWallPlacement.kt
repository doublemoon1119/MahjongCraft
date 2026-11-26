package com.doublemoon1119.mahjongcraft.logic.table.layout

/**
 * 一張仍位於牌牆中的牌之抽象實體位置。
 *
 * @property position 由規則牌牆拓樸提供的基本格位。
 * @property offset 相對基本格位、以牌張尺寸正規化的位移。
 * @property orientation 相對基本格位的離散朝向。
 */
data class TileWallPlacement(
    val position: TileWallPosition,
    val offset: TileWallPlacementOffset = TileWallPlacementOffset.Zero,
    val orientation: TileWallPlacementOrientation = TileWallPlacementOrientation.DEFAULT,
)
