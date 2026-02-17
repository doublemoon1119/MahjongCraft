package com.doublemoon1119.mahjongcraft.model.riichi

import com.doublemoon1119.mahjongcraft.model.Tile
import com.doublemoon1119.mahjongcraft.model.TileWall
import com.doublemoon1119.mahjongcraft.model.TileWallFactory

/**
 * 日本麻將 (Riichi) 牌山生成工廠。
 * * 總數為 136 張，不包含花牌。
 * @property redFiveCount 每種數牌花色中赤五張的數量，預設為各 1 張。
 */
class RiichiWallFactory(private val redFiveCount: Int = 1) : TileWallFactory {
    override fun create(): TileWall {
        val tiles = mutableListOf<Tile>()

        // 生成數牌：萬、筒、條 (各 4 張)
        Tile.Suit.entries.forEach { suit ->
            for (value in 1..9) {
                repeat(4) { count ->
                    val isRed = value == 5 && count < redFiveCount
                    tiles.add(Tile.Numeric(suit, value, isRed))
                }
            }
        }

        // 生成字牌：風牌與三元牌 (各 4 張)
        val honors = listOf(
            Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North,
            Tile.Honor.White, Tile.Honor.Green, Tile.Honor.Red
        )
        honors.forEach { honor ->
            repeat(4) { tiles.add(honor) }
        }

        return TileWall(tiles)
    }
}