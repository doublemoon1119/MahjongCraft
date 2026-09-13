package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition

/**
 * 為第一次日麻槓牌補摸建立足以驗證 Flow 發布行為的權威實體牌牆布局。
 *
 * 最後一墩依日麻 policy 所需排列成下層補充牌與上層待下降牌；其餘牌只需維持唯一格位。
 */
internal fun TableState.withFirstKanPhysicalWallLayout(): TableState {
    val liveTiles = tileWall.getAllTiles()
    require(liveTiles.size >= 2) { "First-kan physical wall fixture requires at least two live tiles" }
    val wallTiles = liveTiles + reservedWallTiles
    val placements = wallTiles.mapIndexed { index, tile ->
        val normalizedIndex = when (index) {
            liveTiles.lastIndex - 1 -> liveTiles.lastIndex
            liveTiles.lastIndex -> liveTiles.lastIndex - 1
            else -> index
        }
        tile.id to TileWallPlacement(
            TileWallPosition(
                side = normalizedIndex / TILES_PER_SIDE,
                stack = (normalizedIndex % TILES_PER_SIDE) / TILES_PER_STACK,
                layer = normalizedIndex % TILES_PER_STACK,
            ),
        )
    }.toMap()
    return copy(physicalWallLayout = TileWallPhysicalLayout(placements))
}

/** 一面日麻牌牆的牌張數。 */
private const val TILES_PER_SIDE = 34

/** 一墩牌的層數。 */
private const val TILES_PER_STACK = 2
