package com.doublemoon1119.mahjongcraft.model.taiwan

import com.doublemoon1119.mahjongcraft.model.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.model.base.Tile
import com.doublemoon1119.mahjongcraft.model.table.TileWall
import com.doublemoon1119.mahjongcraft.model.table.TileWallFactory
import java.util.*

/**
 * 台灣麻將牌山生成工廠。
 *
 * 負責生成具備唯一識別碼的 144 張台灣麻將牌（含 8 張花牌）。
 */
class TaiwanWallFactory : TileWallFactory {
    override fun create(): TileWall {
        val tiles = mutableListOf<IdentifiedTile>()

        // 1. 生成基礎數牌 (無赤牌)
        Tile.Suit.entries.forEach { suit ->
            for (value in 1..9) {
                repeat(4) {
                    val tile = Tile.Numeric(suit, value, isRed = false)
                    tiles.add(IdentifiedTile(UUID.randomUUID(), tile))
                }
            }
        }

        // 2. 生成字牌
        val honors = listOf(
            Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North,
            Tile.Honor.Red, Tile.Honor.Green, Tile.Honor.White
        )
        honors.forEach { honor ->
            repeat(4) {
                tiles.add(IdentifiedTile(UUID.randomUUID(), honor))
            }
        }

        // 3. 生成花牌
        val flowers = listOf(
            Tile.Flower.Spring, Tile.Flower.Summer, Tile.Flower.Autumn, Tile.Flower.Winter,
            Tile.Flower.Plum, Tile.Flower.Orchid, Tile.Flower.Bamboo, Tile.Flower.Chrysanthemum
        )
        flowers.forEach { flower ->
            tiles.add(IdentifiedTile(UUID.randomUUID(), flower))
        }

        return TileWall(tiles)
    }
}