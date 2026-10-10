package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import kotlinx.serialization.Serializable

/**
 * 一張牌之權威實體牌牆位置 persistence DTO。
 *
 * @property side 牌牆側邊索引。
 * @property stack 牌牆堆疊索引。
 * @property layer 堆疊中的高度索引。
 * @property alongWallStacks 沿牌牆方向的偏移。
 * @property towardTableCenterTiles 朝桌面中心方向的偏移。
 * @property upwardLayers 向上方向的偏移。
 * @property orientation 牌張朝向。
 */
@Serializable
data class TileWallPlacementPersistenceDto(
    val side: Int,
    val stack: Int,
    val layer: Int,
    val alongWallStacks: Double,
    val towardTableCenterTiles: Double,
    val upwardLayers: Double,
    val orientation: TileWallPlacementOrientationPersistenceDto,
)
