package com.doublemoon1119.mahjongcraft.domain.rules.taiwan

import com.doublemoon1119.mahjongcraft.domain.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.table.TileWall
import com.doublemoon1119.mahjongcraft.domain.table.TileWallFactory
import java.util.UUID

/**
 * 台灣麻將牌山生成工廠。
 *
 * 負責生成具備唯一識別碼的台灣麻將牌。
 * 支援根據 [TaiwanRuleConfig] 的設定決定是否加入花牌。
 *
 * @property config 台灣麻將的規則配置。
 */
class TaiwanWallFactory(private val config: TaiwanRuleConfig) : TileWallFactory {

    /**
     * 建立台灣麻將牌山實體。
     *
     * 生成邏輯包含：
     * 1. 基礎數牌 (萬、筒、條) 各 36 張，固定無赤牌。
     * 2. 字牌 (東南西北、中發白) 各 4 張，總計 28 張。
     * 3. 根據配置 [TaiwanRuleConfig.useFlowerTiles] 決定是否加入 8 張花牌 (春夏秋冬、梅蘭竹菊)。
     *
     * @return 包含 136 或 144 張麻將牌的 [TileWall]。
     */
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

        // 3. 根據配置決定是否加入花牌
        if (config.useFlowerTiles) {
            val flowers = listOf(
                Tile.Flower.Spring, Tile.Flower.Summer, Tile.Flower.Autumn, Tile.Flower.Winter,
                Tile.Flower.Plum, Tile.Flower.Orchid, Tile.Flower.Bamboo, Tile.Flower.Chrysanthemum
            )
            flowers.forEach { flower ->
                tiles.add(IdentifiedTile(UUID.randomUUID(), flower))
            }
        }

        return TileWall(tiles)
    }
}