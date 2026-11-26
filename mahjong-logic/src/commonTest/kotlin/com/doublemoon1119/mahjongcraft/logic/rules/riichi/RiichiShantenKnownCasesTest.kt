package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenTestFixtures.handOf
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.withRedFives
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 立直麻將向聽計算器的固定規則案例，避免依賴另一套實作當作測試答案。 */
class RiichiShantenKnownCasesTest {
    /** 受測立直麻將向聽計算器。 */
    private val calculator = RiichiShantenCalculator()

    /** 標準型完整手牌應回報和牌。 */
    @Test
    fun `complete standard hand is complete`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(
                m(1), m(2), m(3), p(1), p(2), p(3), s(1), s(2), s(3),
                Tile.Honor.East, Tile.Honor.East, Tile.Honor.East, Tile.Honor.Red, Tile.Honor.Red,
            ),
        )

        assertEquals(ShantenResult.Complete, calculator.calculate(hand))
    }

    /** 13 張標準型手牌應回報聽牌與正確的等待牌順序。 */
    @Test
    fun `standard tenpai preserves tile order in waits`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(
                m(1), m(2), m(3), p(1), p(2), p(3), s(1), s(2), s(3),
                Tile.Honor.East, Tile.Honor.East, m(4), m(5),
            ),
        )
        val result = assertIs<ShantenResult.Tenpai>(calculator.calculate(hand))

        assertEquals(listOf(m(3), m(6)), result.winningTiles)
    }

    /** 沒有雀頭的空手牌應回報最高的標準型向聽數。 */
    @Test
    fun `empty hand has eight shanten`() {
        val result = assertIs<ShantenResult.NotTenpai>(calculator.calculate(FakeHandFactory.create(emptyList())))

        assertEquals(8, result.shanten)
    }

    /** 七對子與國士無雙的完整手牌應優先判定為和牌。 */
    @Test
    fun `seven pairs and thirteen orphans are complete`() {
        val sevenPairs = FakeHandFactory.create(
            tiles = listOf(m(1), m(1), m(2), m(2), m(3), m(3), m(4), m(4), m(5), m(5), m(6), m(6), m(7), m(7)),
        )
        val kokushi = FakeHandFactory.create(
            tiles = listOf(
                m(1), m(9), p(1), p(9), s(1), s(9), Tile.Honor.East, Tile.Honor.South,
                Tile.Honor.West, Tile.Honor.North, Tile.Honor.White, Tile.Honor.Green, Tile.Honor.Red, Tile.Honor.East,
            ),
        )

        assertEquals(ShantenResult.Complete, calculator.calculate(sevenPairs))
        assertEquals(ShantenResult.Complete, calculator.calculate(kokushi))
    }

    /** 赤五按普通五計算，13 張聽牌時等待牌回報普通五。 */
    @Test
    fun `red five counts as a plain five for shanten and waits`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(
                m(1), m(2), m(3), p(1), p(2), p(3), s(1), s(2), s(3),
                Tile.Honor.East, Tile.Honor.East, Tile.Honor.East, Tile.Numeric(Tile.Suit.Character, 5),
            ),
        ).withRedFives(Tile.Suit.Character)
        val result = assertIs<ShantenResult.Tenpai>(calculator.calculate(hand))

        assertEquals(listOf(m(5)), result.winningTiles)
    }

    /** 三人麻將常用牌池中的手牌仍依標準牌型計算，且不包含萬子中張。 */
    @Test
    fun `three-player tile pool hand calculates normally`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(
                p(1), p(2), p(3), s(1), s(2), s(3), m(1), m(1), m(1),
                Tile.Honor.East, Tile.Honor.East, p(4), p(5),
            ),
        )
        val result = assertIs<ShantenResult.Tenpai>(calculator.calculate(hand))

        assertEquals(listOf(p(3), p(6)), result.winningTiles)
    }

    /** 兩組面子、一組搭子與雀頭，其餘是孤張字牌：還差兩手，為 2 向聽。 */
    @Test
    fun `two complete groups with one partial group is two shanten`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(
                m(1), m(2), m(3), p(1), p(2), p(3), s(1), s(1), m(4), m(5),
                Tile.Honor.East, Tile.Honor.South, Tile.Honor.West,
            ),
        )

        assertEquals(2, assertIs<ShantenResult.NotTenpai>(calculator.calculate(hand)).shanten)
    }

    /** 一組面子、兩組嵌張搭子與雀頭，其餘是孤張字牌：七對子與國士無雙都更遠，為 3 向聽。 */
    @Test
    fun `one complete group with two partial groups is three shanten`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(
                m(1), m(2), m(3), s(1), s(1), m(5), m(7), p(4), p(6),
                Tile.Honor.East, Tile.Honor.South, Tile.Honor.West, Tile.Honor.North,
            ),
        )

        assertEquals(3, assertIs<ShantenResult.NotTenpai>(calculator.calculate(hand)).shanten)
    }

    /** 副露與槓子的組數會納入標準型判定。 */
    @Test
    fun `open melds and kans count as completed groups`() {
        val standing = listOf(m(1), m(2), m(3), p(2), p(2))

        assertEquals(ShantenResult.Complete, calculator.calculate(handOf(standing, meldCount = 3)))
        assertEquals(ShantenResult.Complete, calculator.calculate(handOf(listOf(p(2), p(2)), meldCount = 4)))
    }

    /** 建立萬子牌。 */
    private fun m(value: Int): Tile.Numeric = Tile.Numeric(Tile.Suit.Character, value)

    /** 建立筒子牌。 */
    private fun p(value: Int): Tile.Numeric = Tile.Numeric(Tile.Suit.Dot, value)

    /** 建立條子牌。 */
    private fun s(value: Int): Tile.Numeric = Tile.Numeric(Tile.Suit.Bamboo, value)
}
