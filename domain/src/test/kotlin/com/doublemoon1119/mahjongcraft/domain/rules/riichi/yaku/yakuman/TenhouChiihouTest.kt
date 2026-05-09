package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.domain.base.*
import com.doublemoon1119.mahjongcraft.testing.domain.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.domain.rules.riichi.FakeRiichiHandValueContextFactory
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.table.Wind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 天和 (Tenhou) 與地和 (Chiihou) 役種檢測測試。
 *
 * 測試以下役種：
 * - 天和 (Tenhou) - 役滿
 * - 地和 (Chiihou) - 役滿
 *
 * @see com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandValueCalculator
 */
class TenhouChiihouTest : RiichiHandValueCalculatorTestBase() {

    private fun createBasicHand(): List<Tile> = listOf(
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
        Tile.Numeric(Tile.Suit.Dot, 1),
        Tile.Numeric(Tile.Suit.Dot, 1),
        Tile.Numeric(Tile.Suit.Dot, 1)
    )

    /**
     * 測試天和 - 親家在第一巡自摸。
     *
     * 條件：
     * - 親家（seatWind == roundWind）
     * - 第一巡（isFirstTurn = true）
     * - 自摸（isTsumo = true）
     *
     * 天和：役滿
     */
    @Test
    fun `test Tenhou tsumo as dealer`() {
        val hand = FakeHandFactory.create(createBasicHand())
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = FakeRiichiHandValueContextFactory.create(
            hand = hand,
            winningTile = winningTile,
            isTsumo = true,
            roundWind = Wind.EAST,
            seatWind = Wind.EAST,
            isFirstTurn = true
        )
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertTrue(
            result.yakuResults.any { it.yaku == YakuType.Tenhou },
            "Should contain Tenhou, got: ${result.yakuResults.map { it.yaku }}"
        )
    }

    /**
     * 測試地和 - 子家在第一巡自摸。
     *
     * 條件：
     * - 子家（seatWind != roundWind）
     * - 第一巡（isFirstTurn = true）
     * - 自摸（isTsumo = true）
     *
     * 地和：役滿
     */
    @Test
    fun `test chiihou tsumo as non-dealer`() {
        val hand = FakeHandFactory.create(createBasicHand())
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = FakeRiichiHandValueContextFactory.create(
            hand = hand,
            winningTile = winningTile,
            isTsumo = true,
            roundWind = Wind.EAST,
            seatWind = Wind.SOUTH,
            isFirstTurn = true
        )
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertTrue(
            result.yakuResults.any { it.yaku == YakuType.Chiihou },
            "Should contain Chiihou, got: ${result.yakuResults.map { it.yaku }}"
        )
    }

    /**
     * 測試天和 - 非第一巡不成立。
     *
     * 條件：
     * - 親家
     * - 非第一巡（isFirstTurn = false）
     * - 自摸
     *
     * 不應獲得天和。
     */
    @Test
    fun `test Tenhou not valid after first turn`() {
        val hand = FakeHandFactory.create(createBasicHand())
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = FakeRiichiHandValueContextFactory.create(
            hand = hand,
            winningTile = winningTile,
            isTsumo = true,
            roundWind = Wind.EAST,
            seatWind = Wind.EAST,
            isFirstTurn = false
        )
        val result = calculator.calculate(context)

        assertFalse(
            result.yakuResults.any { it.yaku == YakuType.Tenhou },
            "Should not contain Tenhou when not first turn, got: ${result.yakuResults.map { it.yaku }}"
        )
    }

    /**
     * 測試地和 - 榮和不成立。
     *
     * 條件：
     * - 子家
     * - 第一巡
     * - 榮和（isTsumo = false）
     *
     * 不應獲得地和。
     */
    @Test
    fun `test chiihou not valid for ron`() {
        val hand = FakeHandFactory.create(createBasicHand())
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = FakeRiichiHandValueContextFactory.create(
            hand = hand,
            winningTile = winningTile,
            isTsumo = false,
            roundWind = Wind.EAST,
            seatWind = Wind.SOUTH,
            isFirstTurn = true
        )
        val result = calculator.calculate(context)

        assertFalse(
            result.yakuResults.any { it.yaku == YakuType.Chiihou },
            "Should not contain Chiihou for Ron, got: ${result.yakuResults.map { it.yaku }}"
        )
    }
}
