package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.honor

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.FakeRiichiHandValueContextFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 立直麻將手牌番數計算機之字牌役測試。
 *
 * 測試內容涵蓋場風、自風、役牌（三元牌）等役種。
 *
 * @see com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueCalculator
 */
class HonorYakuTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試場風 - 東場。
     *
     * 手牌有東風刻子，應獲得 1 翻。
     */
    @Test
    fun `test round wind east`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Dot, 2)

        val context = FakeRiichiHandValueContextFactory.create(
            hand = hand,
            winningTile = winningTile,
            isTsumo = true,
            roundWind = Wind.EAST,
            seatWind = Wind.SOUTH
        )
        val result = calculator.calculate(context)

        val roundWindResult = result.yakuResults.find { it.yaku == YakuType.RoundWind }
        assertEquals(1, roundWindResult?.han, "RoundWind should be 1 han")
    }

    /**
     * 測試自風 - 南家。
     *
     * 手牌有南風刻子，應獲得 1 翻。
     */
    @Test
    fun `test seat wind south`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Honor.South,
                Tile.Honor.South,
                Tile.Honor.South,
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Dot, 2)

        val context = FakeRiichiHandValueContextFactory.create(
            hand = hand,
            winningTile = winningTile,
            isTsumo = true,
            roundWind = Wind.EAST,
            seatWind = Wind.SOUTH
        )
        val result = calculator.calculate(context)

        val seatWindResult = result.yakuResults.find { it.yaku == YakuType.SeatWind }
        assertEquals(1, seatWindResult?.han, "SeatWind should be 1 han")
    }

    /**
     * 測試役牌 - 中。
     *
     * 手牌有中發刻子，應獲得 2 翻（役牌 x2）。
     */
    @Test
    fun `test dragon yakuhai`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Green,
                Tile.Honor.Green,
                Tile.Honor.Green,
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Dot, 2)

        val context = FakeRiichiHandValueContextFactory.create(
            hand = hand,
            winningTile = winningTile,
            isTsumo = true,
            roundWind = Wind.EAST,
            seatWind = Wind.SOUTH
        )
        val result = calculator.calculate(context)

        val dragonResults = result.yakuResults.filter { it.yaku == YakuType.Dragon }
        assertEquals(2, dragonResults.size, "Should have 2 Dragon yaku")
        assertEquals(2, dragonResults.sumOf { it.han }, "Dragon should be 2 han total")
    }

    /**
     * 測試場風與自風相同時可以共存。
     *
     * 東場東家，手牌有東風刻子，應獲得 2 翻（場風 + 自風）。
     */
    @Test
    fun `test round wind equals seat wind`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Dot, 2)

        val context = FakeRiichiHandValueContextFactory.create(
            hand = hand,
            winningTile = winningTile,
            isTsumo = true,
            roundWind = Wind.EAST,
            seatWind = Wind.EAST
        )
        val result = calculator.calculate(context)

        val roundWindResult = result.yakuResults.find { it.yaku == YakuType.RoundWind }
        val seatWindResult = result.yakuResults.find { it.yaku == YakuType.SeatWind }

        assertEquals(1, roundWindResult?.han, "RoundWind should be 1 han")
        assertEquals(1, seatWindResult?.han, "SeatWind should be 1 han")
    }

    /**
     * 測試字牌不足 3 張時不成立。
     *
     * 手牌只有 2 張東風，應不成立場風。
     */
    @Test
    fun `test honor not enough returns null`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 6)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 2)

        val context = FakeRiichiHandValueContextFactory.create(
            hand = hand,
            winningTile = winningTile,
            isTsumo = true,
            roundWind = Wind.EAST,
            seatWind = Wind.SOUTH
        )
        val result = calculator.calculate(context)

        val roundWindResult = result.yakuResults.find { it.yaku == YakuType.RoundWind }
        assertNull(roundWindResult, "Should not have RoundWind with only 2 honors")
    }
}
