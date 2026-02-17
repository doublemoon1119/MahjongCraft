package com.doublemoon1119.mahjongcraft.model.taiwan

import com.doublemoon1119.mahjongcraft.model.Tile
import com.doublemoon1119.mahjongcraft.model.TileWall
import com.doublemoon1119.mahjongcraft.model.TileWallFactory

/**
 * 台灣麻將牌山生成工廠。
 * * 總數為 144 張，包含基礎 136 張與 8 張花牌。
 */
class TaiwanWallFactory : TileWallFactory {
    override fun create(): TileWall {
        val tiles = mutableListOf<Tile>()

        // 1. 生成基礎數牌 (無赤牌)
        Tile.Suit.entries.forEach { suit ->
            for (value in 1..9) {
                repeat(4) { tiles.add(Tile.Numeric(suit, value, isRed = false)) }
            }
        }

        // 2. 生成字牌
        val honors = listOf(
            Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North,
            Tile.Honor.Red, Tile.Honor.Green, Tile.Honor.White
        )
        honors.forEach { honor ->
            repeat(4) { tiles.add(honor) }
        }

        // 3. 生成花牌
        val flowers = listOf(
            Tile.Flower.Spring, Tile.Flower.Summer, Tile.Flower.Autumn, Tile.Flower.Winter,
            Tile.Flower.Plum, Tile.Flower.Orchid, Tile.Flower.Bamboo, Tile.Flower.Chrysanthemum
        )
        flowers.forEach { tiles.add(it) }

        return TileWall(tiles)
    }
}