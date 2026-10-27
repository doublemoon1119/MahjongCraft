package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.TileWallFactory
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * 以選中的萬子點數建立簡易配對牌山，並檢查牌數足夠供完整玩家名單起手及摸牌。
 *
 * @property playerCount 本局完整玩家人數。
 * @property wallTileValues 加入牌山的一萬至八萬點數，每種四張。
 * @property random 用來洗牌的亂數來源。
 */
class ExamplePairingWallFactory(
    private val playerCount: Int,
    private val wallTileValues: Set<Int> = (1..8).toSet(),
    private val random: Random = Random.Default,
) : TileWallFactory {
    override fun create(): TileWall {
        require(wallTileValues.all { it in 1..8 }) { "Pairing wall values must be between 1 and 8" }
        require(playerCount in 2..4) { "Example pairing supports two to four players" }
        require(wallTileValues.size * 4 >= playerCount * 3 + 1) {
            "Selected tile values do not provide enough tiles for $playerCount players"
        }
        val tiles = buildList {
            for (value in wallTileValues.sorted()) {
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
