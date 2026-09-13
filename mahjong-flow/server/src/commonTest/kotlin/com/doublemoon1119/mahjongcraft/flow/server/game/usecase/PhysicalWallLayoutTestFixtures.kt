package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory

/**
 * 為第一次日麻槓牌補摸建立足以驗證 Flow 發布行為的權威實體牌牆布局。
 *
 * 王牌集中於開門面，前八格保存開局王牌，後兩格預留四次槓的補入位置；活牌末端另放在相鄰牆面，
 * 以便驗證第一次補入及上層下降的兩階段 transition。
 */
internal fun TableState.withFirstKanPhysicalWallLayout(): TableState {
    val liveTiles = tileWall.getAllTiles()
    require(liveTiles.size >= 2) { "First-kan physical wall fixture requires at least two live tiles" }

    val livePairIds = liveTiles.takeLast(2).map { it.id }.toSet()
    val availableLivePositions = buildList {
        for (side in 1 until WALL_SIDE_COUNT) {
            for (stack in 0 until STACKS_PER_SIDE) {
                for (layer in 0..1) add(TileWallPosition(side, stack, layer))
            }
        }
    }.filterNot { it.side == LIVE_PAIR_SIDE && it.stack == LIVE_PAIR_STACK }
    val ordinaryLiveTiles = liveTiles.filterNot { it.id in livePairIds }
    require(ordinaryLiveTiles.size <= availableLivePositions.size) {
        "First-kan physical wall fixture has too many live tiles"
    }

    val placements = buildMap {
        ordinaryLiveTiles.zip(availableLivePositions).forEach { (tile, position) ->
            put(tile.id, TileWallPlacement(position))
        }
        put(liveTiles[liveTiles.lastIndex - 1].id, TileWallPlacement(TileWallPosition(LIVE_PAIR_SIDE, LIVE_PAIR_STACK, 1)))
        put(liveTiles.last().id, TileWallPlacement(TileWallPosition(LIVE_PAIR_SIDE, LIVE_PAIR_STACK, 0)))
        reservedWallTiles.forEachIndexed { index, tile ->
            val position = when (index) {
                0 -> TileWallPosition(RESERVED_SIDE, RESERVED_HEAD_STACK, 0)
                1 -> TileWallPosition(RESERVED_SIDE, RESERVED_HEAD_STACK - 1, 0)
                else -> TileWallPosition(
                    RESERVED_SIDE,
                    RESERVED_HEAD_STACK - (index / 2 + 1),
                    if (index % 2 == 0) 1 else 0,
                )
            }
            put(tile.id, TileWallPlacement(position, RESERVED_WALL_OFFSET))
        }
    }
    return copy(physicalWallLayout = TileWallPhysicalLayout(placements))
}

/** 以 [firstSupplementalTile] 為第一張嶺上牌建立完整十四張日麻王牌測試資料。 */
internal fun completeRiichiReservedWall(firstSupplementalTile: IdentifiedTile): List<IdentifiedTile> = listOf(firstSupplementalTile) +
    List(RIICHI_RESERVED_TILE_COUNT - 1) {
        FakeIdentifiedTileFactory.create(Tile.Honor.White)
    }

/** 四面牌牆。 */
private const val WALL_SIDE_COUNT = 4

/** 日麻每面牌牆有十七墩。 */
private const val STACKS_PER_SIDE = 17

/** 測試中集中王牌的開門面。 */
private const val RESERVED_SIDE = 0

/** 王牌軌道最接近開門缺口的墩位。 */
private const val RESERVED_HEAD_STACK = 15

/** 活牌末端測試牌墩所在牆面。 */
private const val LIVE_PAIR_SIDE = 1

/** 活牌末端測試牌墩序號。 */
private const val LIVE_PAIR_STACK = 0

/** 日麻固定王牌張數。 */
private const val RIICHI_RESERVED_TILE_COUNT = 14

/** 王牌相對基本牌牆形成分界的位移。 */
private val RESERVED_WALL_OFFSET = TileWallPlacementOffset(alongWallStacks = 0.25)
