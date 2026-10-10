package com.doublemoon1119.mahjongcraft.logic.table.layout

/**
 * 一張牌相對於其基本牌牆格位的正規化位移。
 *
 * 各軸使用麻將牌自身尺寸作為單位，讓規則可以描述縫隙、下降與疊放，而不依賴特定平台的世界座標。
 *
 * @property alongWallStacks 沿所在牌牆面的位移，一單位等於一墩寬度。
 * @property towardTableCenterTiles 朝桌心方向的位移，一單位等於一張牌的長度；負值代表遠離桌心。
 * @property upwardLayers 垂直方向的位移，一單位等於一層牌厚；負值代表向下。
 */
data class TileWallPlacementOffset(
    val alongWallStacks: Double = 0.0,
    val towardTableCenterTiles: Double = 0.0,
    val upwardLayers: Double = 0.0,
) {
    init {
        require(alongWallStacks.isFinite()) { "alongWallStacks must be finite" }
        require(towardTableCenterTiles.isFinite()) { "towardTableCenterTiles must be finite" }
        require(upwardLayers.isFinite()) { "upwardLayers must be finite" }
    }

    /** 不施加額外位移的共用值。 */
    companion object {
        /** 保持基本牌牆格位的零位移。 */
        val Zero: TileWallPlacementOffset = TileWallPlacementOffset()
    }
}
