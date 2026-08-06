package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.FakeRiichiHandValueContextFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 立直麻將手牌番數計算機之役滿測試。
 *
 * @see com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueCalculator
 */
class YakumanTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試役滿計算 - 單一役滿。
     *
     * 有一個役滿 (四暗刻)，totalHan 應為 -1。
     */
    @Test
    fun `test yakuman calculation single`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.South,
                Tile.Honor.South,
                Tile.Honor.South,
                Tile.Honor.White,
                Tile.Honor.White,
                Tile.Honor.Green,
                Tile.Honor.Green,
            ),
        )
        val winningTile = Tile.Honor.Green

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-1, result.totalHan, "Single yakuman should be -1")
    }

    /**
     * 測試役滿計算 - 雙倍役滿。
     *
     * 有一個雙倍役滿 (四暗刻單騎)，totalHan 應為 -2。
     */
    @Test
    fun `test double yakuman calculation`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.South,
                Tile.Honor.South,
                Tile.Honor.South,
                Tile.Honor.White,
                Tile.Honor.White,
                Tile.Honor.White,
                Tile.Honor.Green,
            ),
        )
        val winningTile = Tile.Honor.Green

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-2, result.totalHan, "Double yakuman should be -2")
    }

    /**
     * 測試役滿計算 - 兩個一般役滿。
     *
     * 有兩個一般役滿 (字一色 + 大三元)，totalHan 應為 -2。
     */
    @Test
    fun `test two yakuman calculation`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.White,
                Tile.Honor.White,
                Tile.Honor.White,
                Tile.Honor.Green,
                Tile.Honor.Green,
                Tile.Honor.Green,
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.West,
                Tile.Honor.West,
            ),
        )
        val winningTile = Tile.Honor.West

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = false)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-2, result.totalHan, "Two yakuman should be -2")
    }

    /**
     * 測試役滿計算 - 雙倍役滿加一般役滿。
     *
     * 有一個雙倍役滿 (大四喜) 和一個一般役滿 (字一色)，totalHan 應為 -3。
     */
    @Test
    fun `test double yakuman plus yakuman calculation`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.South,
                Tile.Honor.South,
                Tile.Honor.South,
                Tile.Honor.West,
                Tile.Honor.West,
                Tile.Honor.West,
                Tile.Honor.Green,
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Honor.North),
                        FakeIdentifiedTileFactory.create(Tile.Honor.North),
                        FakeIdentifiedTileFactory.create(Tile.Honor.North),
                    ),
                    sourceDirection = RelativeDirection.Left,
                ),
            ),
        )
        val winningTile = Tile.Honor.Green

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-3, result.totalHan, "Double yakuman + yakuman should be -3")
    }
}
