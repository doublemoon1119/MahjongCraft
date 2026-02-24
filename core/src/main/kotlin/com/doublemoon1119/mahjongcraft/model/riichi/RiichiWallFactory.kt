package com.doublemoon1119.mahjongcraft.model.riichi

import com.doublemoon1119.mahjongcraft.model.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.model.base.Tile
import com.doublemoon1119.mahjongcraft.model.table.TileWall
import com.doublemoon1119.mahjongcraft.model.table.TileWallFactory
import java.util.*

/**
 * 日本麻將 (Riichi) 牌山生成工廠。
 *
 * 負責生成具備唯一識別碼的 136 張日本麻將牌。
 * @property redFiveCount 每種數牌花色中赤五張的數量，預設為各 1 張。
 */
class RiichiWallFactory(private val redFiveCount: Int = 1) : TileWallFactory {
    override fun create(): TileWall {
        val tiles = mutableListOf<IdentifiedTile>()

        // 生成數牌：萬、筒、條 (各 4 張)
        Tile.Suit.entries.forEach { suit ->
            for (value in 1..9) {
                repeat(4) { count ->
                    val isRed = value == 5 && count < redFiveCount
                    val tile = Tile.Numeric(suit, value, isRed)
                    tiles.add(IdentifiedTile(UUID.randomUUID(), tile))
                }
            }
        }

        // 生成字牌：風牌與三元牌 (各 4 張)
        val honors = listOf(
            Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North,
            Tile.Honor.White, Tile.Honor.Green, Tile.Honor.Red
        )
        honors.forEach { honor ->
            repeat(4) {
                tiles.add(IdentifiedTile(UUID.randomUUID(), honor))
            }
        }

        return TileWall(tiles)
    }
}