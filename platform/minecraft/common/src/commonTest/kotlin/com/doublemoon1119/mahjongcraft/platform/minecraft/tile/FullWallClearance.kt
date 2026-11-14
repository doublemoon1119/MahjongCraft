package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideArrangement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideReference
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide

/** 四面牌牆分別放在局部南、西、北、東的固定桌邊對應。 */
internal val FULL_WALL_SIDE_LAYOUT: MahjongTileWallSideLayout = MahjongTileWallSideLayout.resolve(
    arrangement = TileWallSideArrangement(reference = TileWallSideReference.TABLE, quarterTurns = listOf(0, 3, 2, 1)),
    dealerSide = MahjongTableSide.SOUTH,
)

/**
 * 四面牌牆每個墩位上下兩層都有牌時的位置。
 *
 * @param stacksPerSide 牌牆每面的墩數。
 * @return 全部牌牆位置。
 */
internal fun fullWallPlacements(stacksPerSide: Int): List<TileWallPlacement> = (0 until FULL_WALL_SIDE_LAYOUT.sideCount).flatMap { side ->
    (0 until stacksPerSide).flatMap { stack -> (0..1).map { layer -> TileWallPlacement(TileWallPosition(side, stack, layer)) } }
}

/**
 * 四面牌牆一張都沒被摸走時，從局部南側座位看出去的讓位範圍；完整牌牆四面對稱，其他座位看出去也相同。
 *
 * @param tileLayout 桌面牌位配置。
 * @param stacksPerSide 牌牆每面的墩數。
 * @return 南側座位的讓位範圍。
 */
internal fun fullWallClearance(tileLayout: TableTileLayoutProfile, stacksPerSide: Int): MahjongSeatWallClearance = MahjongTileTableLayout.seatWallClearance(
    MahjongTileTableLayout.wallClearance(FULL_WALL_SIDE_LAYOUT, tileLayout, stacksPerSide, fullWallPlacements(stacksPerSide)),
    MahjongTableSide.SOUTH,
)
