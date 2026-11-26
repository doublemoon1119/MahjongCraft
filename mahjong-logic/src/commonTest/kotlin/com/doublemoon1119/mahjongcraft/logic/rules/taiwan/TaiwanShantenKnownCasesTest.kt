package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenTestFixtures.handOf
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 台灣麻將向聽計算器的固定規則案例，避免依賴另一套實作當作測試答案。 */
class TaiwanShantenKnownCasesTest {
    /** 受測台灣麻將向聽計算器。 */
    private val calculator = TaiwanShantenCalculator()

    /** 17 張五面子一雀頭手牌應回報和牌。 */
    @Test
    fun `seventeen-tile standard hand is complete`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(
                m(1), m(1), m(1), m(2), m(3), m(4), m(5), m(6), m(7),
                m(8), m(8), m(8), Tile.Honor.East, Tile.Honor.East, Tile.Honor.East,
                Tile.Honor.South, Tile.Honor.South,
            ),
        )

        assertEquals(ShantenResult.Complete, calculator.calculate(hand))
    }

    /** 16 張五面子單騎手牌應回報聽牌與單一等待牌。 */
    @Test
    fun `sixteen-tile hand is tenpai`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(
                m(1), m(1), m(1), m(2), m(3), m(4), m(5), m(6), m(7),
                m(8), m(8), m(8), Tile.Honor.East, Tile.Honor.East, Tile.Honor.East, Tile.Honor.South,
            ),
        )
        val result = assertIs<ShantenResult.Tenpai>(calculator.calculate(hand))

        assertEquals(listOf(Tile.Honor.South), result.winningTiles)
    }

    /** 花牌不參與向聽計算，加入整套花牌前後結果應相同。 */
    @Test
    fun `flowers do not affect shanten`() {
        val hand = FakeHandFactory.create(listOf(m(1), m(1), m(1)))
        val withFlowers = FakeHandFactory.create(hand.standingTiles.map { it.tile } + TaiwanTileTypes.createAll())

        assertEquals(calculator.calculate(hand), calculator.calculate(withFlowers))
    }

    /** 16 張手牌有三組面子、一組搭子與雀頭，其餘是孤張字牌：還差兩手，為 2 向聽。 */
    @Test
    fun `three complete groups with one partial group is two shanten`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(
                m(1), m(2), m(3), p(1), p(2), p(3), p(7), p(8), p(9),
                Tile.Honor.East, Tile.Honor.East, m(4), m(5),
                Tile.Honor.South, Tile.Honor.West, Tile.Honor.North,
            ),
        )

        assertEquals(2, assertIs<ShantenResult.NotTenpai>(calculator.calculate(hand)).shanten)
    }

    /** 16 張手牌有兩組面子、兩組嵌張搭子與雀頭，其餘是孤張字牌：為 3 向聽。 */
    @Test
    fun `two complete groups with two partial groups is three shanten`() {
        val hand = FakeHandFactory.create(
            tiles = listOf(
                m(1), m(2), m(3), p(1), p(2), p(3), Tile.Honor.East, Tile.Honor.East,
                m(5), m(7), p(7), p(9),
                Tile.Honor.South, Tile.Honor.West, Tile.Honor.North, Tile.Honor.White,
            ),
        )

        assertEquals(3, assertIs<ShantenResult.NotTenpai>(calculator.calculate(hand)).shanten)
    }

    /** 副露與槓子的組數會納入台灣麻將五面子判定。 */
    @Test
    fun `open melds and kans count as completed groups`() {
        val standing = listOf(m(1), m(2), m(3), p(2), p(2))

        assertEquals(ShantenResult.Complete, calculator.calculate(handOf(standing, meldCount = 4)))
    }

    /** 建立萬子牌。 */
    private fun m(value: Int): Tile.Numeric = Tile.Numeric(Tile.Suit.Character, value)

    /** 建立筒子牌。 */
    private fun p(value: Int): Tile.Numeric = Tile.Numeric(Tile.Suit.Dot, value)
}
