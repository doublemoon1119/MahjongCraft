package com.doublemoon1119.mahjongcraft.logic.table.layout

import kotlin.uuid.Uuid

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
