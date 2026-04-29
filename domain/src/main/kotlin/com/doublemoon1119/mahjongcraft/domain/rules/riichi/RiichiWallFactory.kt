package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.table.TileWall
import com.doublemoon1119.mahjongcraft.domain.table.TileWallFactory
import java.util.*

/**
 * 日本麻將牌山生成工廠。
 *
 * 負責根據 [RiichiRuleConfig] 的設定生成符合日本麻將規範的 136 張牌。
 * 修正了赤寶牌的分佈邏輯，確保萬、筒、條的赤牌數量符合標準分配。
 *
 * @property config 日本麻將的規則配置。
 */
class RiichiWallFactory(private val config: RiichiRuleConfig) : TileWallFactory {

    /**
     * 建立日本麻將牌山實體。
     *
     * @return 包含 136 張已賦予唯一識別碼之麻將牌的 [TileWall]。
     */
    override fun create(): TileWall {
        val tiles = mutableListOf<IdentifiedTile>()

        // 1. 生成數牌：萬、筒、條 (各花色 1~9 號各 4 張)
        Tile.Suit.entries.forEach { suit ->
            // 根據總赤牌數決定當前花色 (Suit) 應有的赤牌數量
            // 3張：萬1, 筒1, 條1 | 4張：萬1, 筒2, 條1
            val redInThisSuit = when (config.redDoraCount) {
                3 -> 1
                4 -> if (suit == Tile.Suit.Dot) 2 else 1
                else -> 0 // NONE 或其他不支援的數量
            }

            for (value in 1..9) {
                repeat(4) { count ->
                    // 僅有 5 號牌可能是赤牌
                    val isRed = value == 5 && count < redInThisSuit
                    val tile = Tile.Numeric(suit, value, isRed)
                    tiles.add(IdentifiedTile(UUID.randomUUID(), tile))
                }
            }
        }

        // 2. 生成字牌：風牌與三元牌 (各 4 張)
        val honors = listOf(
            Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North,
            Tile.Honor.White, Tile.Honor.Green, Tile.Honor.Red
        )
        honors.forEach { honor ->
            repeat(4) {
                tiles.add(IdentifiedTile(UUID.randomUUID(), honor))
            }
        }

        // 3. 洗牌
        tiles.shuffle()

        return TileWall(tiles)
    }
}
