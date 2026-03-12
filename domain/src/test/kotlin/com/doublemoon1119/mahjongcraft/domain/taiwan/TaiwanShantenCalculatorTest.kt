package com.doublemoon1119.mahjongcraft.domain.taiwan

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TaiwanShantenCalculatorTest {

    private val calculator = TaiwanShantenCalculator()

    /**
     * 輔助函式，用於從 Tile 列表快速建立一個 Hand 物件。
     */
    private fun createHand(tiles: List<Tile>): Hand {
        val identifiedTiles = tiles.map { IdentifiedTile(UUID.randomUUID(), it) }
        return Hand(identifiedTiles.toMutableList())
    }

    @Test
    fun `test tenpai hand - 4 melds, 1 pair, 1 tatsu`() {
        // 手牌: 111m, 234m, 567m, 東東東, 南南, 88m (16張)
        // 4 面子, 1 雀頭, 1 搭子 -> 聽牌 (0 shanten)
        val hand = createHand(
            listOf(
                // 4 面子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East,
                // 1 雀頭
                Tile.Honor.South,
                Tile.Honor.South,
                // 1 搭子
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 8)
            )
        )
        val result = calculator.calculate(hand)
        assertEquals(0, result.shanten, "Hand with 4 melds, 1 pair, 1 tatsu should be 0 shanten.")
    }

    @Test
    fun `test tenpai hand - 5 melds, 1 single`() {
        // 手牌: 111m, 234m, 567m, 888m, 東東東, 南 (16張)
        // 5 面子, 1 單張 -> 聽牌 (0 shanten), 聽單騎 南
        val hand = createHand(
            listOf(
                // 5 面子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East,
                // 1 單張
                Tile.Honor.South
            )
        )
        val result = calculator.calculate(hand)
        assertEquals(0, result.shanten, "Hand with 5 melds, 1 single should be 0 shanten.")
    }

    @Test
    fun `test agari hand - 5 melds, 1 pair`() {
        // 手牌: 111m, 234m, 567m, 888m, 東東東, 南南 (17張)
        // 5 面子, 1 雀頭 -> 胡牌 (-1 shanten)
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.South,
                Tile.Honor.South
            )
        )
        val result = calculator.calculate(hand)
        assertEquals(-1, result.shanten, "Hand with 5 melds, 1 pair should be -1 shanten (Agari).")
    }
}
