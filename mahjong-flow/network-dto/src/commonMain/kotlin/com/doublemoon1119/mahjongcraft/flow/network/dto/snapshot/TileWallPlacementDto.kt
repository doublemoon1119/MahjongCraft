package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import kotlinx.serialization.Serializable

/**
 * 一張牌之權威實體牌牆位置網路 DTO。
 *
 * @property side 牌牆邊索引。
 * @property stack 邊上的牌堆索引。
 * @property layer 牌堆中的層索引。
 * @property alongWallStacks 沿牌牆方向的偏移量。
 * @property towardTableCenterTiles 朝桌面中心方向的偏移量。
 * @property upwardLayers 向上方向的偏移層數。
 * @property orientation 牌張朝向。
 */
@Serializable
data class TileWallPlacementDto(
    val side: Int,
    val stack: Int,
    val layer: Int,
    val alongWallStacks: Double,
    val towardTableCenterTiles: Double,
    val upwardLayers: Double,
    val orientation: TileWallPlacementOrientationDto,
)
