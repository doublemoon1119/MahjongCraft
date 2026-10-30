package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileValues
import com.doublemoon1119.mahjongcraft.logic.table.TileWallFactory
import com.doublemoon1119.mahjongcraft.logic.tile.TileComposition
import com.doublemoon1119.mahjongcraft.logic.tile.TileCompositionEntry

/**
 * 日本麻將牌山生成工廠。
 *
 * 負責根據 [RiichiRuleConfig] 的設定生成符合日本麻將規範的 136 張牌。
 * 赤牌總數為三張時各花色一張；四張時五筒兩張、五萬與五索各一張。
 *
 * @property config 日本麻將的規則配置。
 */
class RiichiWallFactory(private val config: RiichiRuleConfig) : TileWallFactory {

    /**
     * 取得日本麻將牌組組成。
     *
     * @return 包含 136 張牌值及張數的組成，不產生牌張身分。
     */
    override fun composition(): TileComposition {
        val tiles = mutableListOf<TileValue>()

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
                    val tile = if (value == 5 && count < redInThisSuit) {
                        RiichiTileValues.redFive(suit)
                    } else {
                        TileValue(Tile.Numeric(suit, value))
                    }
                    tiles.add(tile)
                }
            }
        }

        // 2. 生成字牌：風牌與三元牌 (各 4 張)
        val honors = listOf(
            Tile.Honor.East,
            Tile.Honor.South,
            Tile.Honor.West,
            Tile.Honor.North,
            Tile.Honor.White,
            Tile.Honor.Green,
            Tile.Honor.Red,
        )
        honors.forEach { honor ->
            repeat(4) {
                tiles.add(TileValue(honor))
            }
        }

        return TileComposition(tiles.groupingBy { it }.eachCount().map { (value, count) -> TileCompositionEntry(value, count) })
    }
}
