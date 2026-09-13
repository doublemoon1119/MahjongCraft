package com.doublemoon1119.mahjongcraft.logic.table.layout

import kotlin.uuid.Uuid

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

/**
 * 目前仍位於牌牆中的每張牌之權威抽象實體位置。
 *
 * @property placements 牌張 Uuid 到最終位置的映射；兩張牌不得占用完全相同的位置。
 */
data class TileWallPhysicalLayout(val placements: Map<Uuid, TileWallPlacement>) {
    init {
        require(placements.values.distinct().size == placements.size) {
            "Physical wall layout placements must be unique"
        }
    }

    /**
     * 只保留 [tileIds] 指定的 placement，且拒絕要求目前布局不存在的牌張。
     *
     * @param tileIds 新布局應包含的完整牌張 Uuid 集合。
     * @return placement 內容與本布局一致、成員縮減為 [tileIds] 的新布局。
     */
    fun retainOnly(tileIds: Set<Uuid>): TileWallPhysicalLayout {
        require(placements.keys.containsAll(tileIds)) { "Physical wall layout does not contain every retained tile" }
        return TileWallPhysicalLayout(placements.filterKeys { it in tileIds })
    }
}
