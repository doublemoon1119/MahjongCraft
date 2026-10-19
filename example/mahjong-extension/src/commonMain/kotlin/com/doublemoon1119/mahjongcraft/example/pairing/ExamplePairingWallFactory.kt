package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.TileWallFactory
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * 依完整玩家人數建立簡易配對麻將牌山。
 *
 * @property playerCount 本局完整玩家人數。
 * @property random 用來洗牌的亂數來源。
 */
class ExamplePairingWallFactory(
    private val playerCount: Int,
    private val random: Random = Random.Default,
) : TileWallFactory {
    override fun create(): TileWall {
        require(playerCount in 2..4) { "Example pairing supports two to four players" }
        val maxValue = when (playerCount) {
            2 -> 4
            3 -> 6
            else -> 8
        }
        val tiles = buildList {
            for (value in 1..maxValue) {
                repeat(4) { copyIndex ->
                    val tile = if (value == 1 && copyIndex == 3) {
                        ExamplePairingTileType.invertedOneMan()
                    } else {
                        Tile.Numeric(Tile.Suit.Character, value)
                    }
                    add(IdentifiedTile(Uuid.random(), tile))
                }
            }
        }.toMutableList()
        tiles.shuffle(random)
        return TileWall(tiles)
    }
}
