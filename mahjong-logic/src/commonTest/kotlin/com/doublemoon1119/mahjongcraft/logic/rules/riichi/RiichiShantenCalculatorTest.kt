package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 立直麻將向聽數計算器之單元測試。
 *
 * 測試內容涵蓋標準型、七對子及國士無雙三種胡牌型態的向聽數計算。
 *
 * @see RiichiShantenCalculator
 */
class RiichiShantenCalculatorTest {

    private val calculator = RiichiShantenCalculator()

    /**
     * 測試標準型聽牌手牌。
     *
     * 手牌為 1112345678999m，聽 1m 對倒，向聽數應為 0。
     */
    @Test
    fun `test tenpai standard hand`() {
        // 手牌: 1112345678999m (聽 1m) - 向聽 0
        val hand = FakeHandFactory.create(
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
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )
        val result = calculator.calculate(hand)
        assertTrue(result is ShantenResult.Tenpai, "Tenpai hand should be Tenpai result")
    }

    /**
     * 測試七對子胡牌手牌。
     *
     * 手牌為 7 個不同對子（14 張牌），向聽數應為 -1（已完成）。
     */
    @Test
    fun `test complete hand - seven pairs`() {
        // 7對子 = 14張牌 (門前清)
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 7),
            ),
        )
        val result = calculator.calculate(hand)
        assertTrue(result is ShantenResult.Complete, "Seven pairs complete hand should be Complete")
    }

    /**
     * 測試七對子聽牌手牌。
     *
     * 手牌為 6 對子 + 1 張單張（13 張牌），已聽七對子，向聽數應為 0。
     */
    @Test
    fun `test seven pairs one away`() {
        // 6對子 + 一張單張 = 聽七對子 (門前清)
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
            ),
        )
        val result = calculator.calculate(hand)
        assertTrue(result is ShantenResult.Tenpai, "Six pairs should be Tenpai for seven pairs")
    }

    /**
     * 測試國士無雙胡牌手牌。
     *
     * 手牌包含 13 種么九牌各一張及一張雀頭（14 張牌），向聽數應為 -1（已完成）。
     */
    @Test
    fun `test complete hand - kokushi`() {
        // 國士無雙胡牌: 13種么九牌各一張 + 其中一張有兩張 = 14張
        // 手牌: 1m,9m,1p,9p,1s,9s,東,南,西北,白發中 + 雙東
        val hand = FakeHandFactory.create(
            listOf(
                // 萬子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 9),
                // 筒子
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 9),
                // 條子
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                // 字牌
                Tile.Honor.East,
                Tile.Honor.South,
                Tile.Honor.West,
                Tile.Honor.North,
                Tile.Honor.White,
                Tile.Honor.Green,
                Tile.Honor.Red,
                // 雀頭 (東)
                Tile.Honor.East,
            ),
        )
        val result = calculator.calculate(hand)
        assertTrue(result is ShantenResult.Complete, "Kokushi complete hand should be Complete")
    }

    /**
     * 測試國士無雙聽牌手牌。
     *
     * 手牌包含 13 種么九牌各一張（13 張牌），已聽最後一張，向聽數應為 0。
     */
    @Test
    fun `test tenpai hand - kokushi`() {
        // 國士無雙聽牌: 13種么九牌各一張 = 13張 (聽最後一張)
        // 手牌: 1m,9m,1p,9p,1s,9s,東,南,西北,白發中 (13張)
        val hand = FakeHandFactory.create(
            listOf(
                // 萬子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 9),
                // 筒子
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 9),
                // 條子
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                // 字牌
                Tile.Honor.East,
                Tile.Honor.South,
                Tile.Honor.West,
                Tile.Honor.North,
                Tile.Honor.White,
                Tile.Honor.Green,
                Tile.Honor.Red,
            ),
        )
        val result = calculator.calculate(hand)
        assertTrue(result is ShantenResult.Tenpai, "Kokushi tenpai hand should be Tenpai")
    }

    /**
     * 測試國士無雙一間聽手牌。
     *
     * 手牌僅有 12 種么九牌（12 張牌），需要再獲取 1 種么九牌，向聽數應為 1。
     */
    @Test
    fun `test kokushi one away`() {
        // 國士無雙一間聽: 12種么九牌 = 12張 (需要再補1種)
        // 手牌: 1m,9m,1p,9p,1s,9s,東,南,西北,白發 (12張)
        val hand = FakeHandFactory.create(
            listOf(
                // 萬子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 9),
                // 筒子
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 9),
                // 條子
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                // 字牌 (少一張)
                Tile.Honor.East,
                Tile.Honor.South,
                Tile.Honor.West,
                Tile.Honor.North,
                Tile.Honor.White,
                Tile.Honor.Green,
            ),
        )
        val result = calculator.calculate(hand)
        val notTenpaiResult = result as ShantenResult.NotTenpai
        assertEquals(1, notTenpaiResult.shanten, "Kokushi one away should have 1 shanten")
    }
}
