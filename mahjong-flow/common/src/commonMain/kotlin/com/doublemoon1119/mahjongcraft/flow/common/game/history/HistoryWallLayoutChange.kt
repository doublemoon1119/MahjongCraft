package com.doublemoon1119.mahjongcraft.flow.common.game.history

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import kotlin.uuid.Uuid

/**
 * 實體牌牆位置的局部變化。
 *
 * @property removedTileIds 離開牌牆的牌 UUID。
 * @property updatedPlacements 位置或方向改變的牌 UUID 與新位置。
 */
data class HistoryWallLayoutChange(
    val removedTileIds: Set<Uuid>,
    val updatedPlacements: Map<Uuid, TileWallPlacement>,
) {
    /**
     * 將局部變化套用到前一個布局；新位置取代同一張牌的舊位置。
     *
     * @param before 交易前的牌牆布局。
     * @return 移除指定牌張並套用新位置後的布局。
     */
    fun applyTo(before: TileWallPhysicalLayout): TileWallPhysicalLayout = TileWallPhysicalLayout(
        before.placements.filterKeys { it !in removedTileIds } + updatedPlacements,
    )
}
