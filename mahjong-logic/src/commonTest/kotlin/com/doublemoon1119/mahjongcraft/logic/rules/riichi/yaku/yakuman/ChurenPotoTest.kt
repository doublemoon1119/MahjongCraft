package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.FakeRiichiHandValueContextFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 九蓮寶燈役種檢測測試。
 *
 * 測試以下役種：
 * - 九蓮寶燈 (Churen Poto) - 役滿
 * - 九蓮寶燈九面 (Churen Poto 9-men) - 雙倍役滿
 *
 * @see calculateChurenPoto
 * @see com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueCalculator
 */
class ChurenPotoTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試九蓮寶燈九面（多面聽牌）。
     *
     * 牌型：111m 999m 2345678m
     * 聽牌：1-9 任意一張
     * 應為雙倍役滿 (Double Yakuman)
     *
     * 注意： winningTile 不能與手牌中任何一張相同，否則會被識別為 Chiitoitsu
     */
    @Test
    fun `test churen poto 9-men`() {
        // Hand: 111m 999m 2345678m (13 tiles) - 這個牌型有 2 個刻子 + 7 張單張
        val hand = FakeHandFactory.create(
            listOf(
                // 111m (3)
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                // 2345678m (7)
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                // 999m (3)
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )
        // 自摸 8m
        val winningTile2 = Tile.Numeric(Tile.Suit.Character, 8)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile2, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be double yakuman")
        assertEquals(-2, result.totalHan, "Total han should be -2 for double yakuman")
        assertEquals(1, result.yakuResults.size, "Should have 1 yaku result")
        assertEquals(YakuType.ChurenPoto9, result.yakuResults[0].yaku, "Yaku should be ChurenPoto9")
    }

    /**
     * 測試九蓮寶燈 - 一般型（單騎聽牌）。
     *
     * 牌型：111m 999m 234m 567m 8m
     * 聽牌：8m
     * 應為役滿 (Yakuman)
     */
    @Test
    fun `test churen poto general`() {
        // Hand: 111m 999m 2345677m (13 tiles)
        val hand = FakeHandFactory.create(
            listOf(
                // 111m (3)
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                // 999m (3)
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                // 2345677m (7)
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 7),
            ),
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 8)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-1, result.totalHan, "Total han should be -1")
        assertEquals(1, result.yakuResults.size, "Should have 1 yaku result")
        assertEquals(YakuType.ChurenPoto, result.yakuResults[0].yaku, "Yaku should be ChurenPoto")
    }

    /**
     * 測試九蓮寶燈 - 非門前清（應為 null）。
     */
    @Test
    fun `test churen poto not menzen returns null`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 234m (3)
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                // 567m (3)
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                // 8m (1)
                Tile.Numeric(Tile.Suit.Character, 8),
                // 999m (3)
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                    ),
                    sourceDirection = RelativeDirection.Left,
                ),
            ),
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 8)

        // isMenzen = false
        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        // 應該不是九蓮寶燈
        val churenPotoResult =
            result.yakuResults.find { it.yaku == YakuType.ChurenPoto || it.yaku == YakuType.ChurenPoto9 }
        assertNull(churenPotoResult, "Should return null when not menzen")
    }

    /**
     * 測試非九蓮寶燈 - 包含不同花色（應為 null）。
     */
    @Test
    fun `test non-churen poto with mixed suits returns null`() {
        // Hand: 111m 999m 234p...
        val hand = FakeHandFactory.create(
            listOf(
                // 111m
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                // 混合花色 p
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 6),
                Tile.Numeric(Tile.Suit.Dot, 7),
                Tile.Numeric(Tile.Suit.Dot, 8),
                // 999m
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )
        val winningTile = Tile.Numeric(Tile.Suit.Dot, 8)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val churenPotoResult =
            result.yakuResults.find { it.yaku == YakuType.ChurenPoto || it.yaku == YakuType.ChurenPoto9 }
        assertNull(churenPotoResult, "Should return null when hand has mixed suits")
    }

    /**
     * 測試非九蓮寶燈 - 缺少 111 或 999（應為 null）。
     */
    @Test
    fun `test non-churen poto without 111 or 999 returns null`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 234m (3)
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                // 567m (3)
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                // 88m (2)
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 8),
                // 湊成 13 張
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
            ),
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val churenPotoResult =
            result.yakuResults.find { it.yaku == YakuType.ChurenPoto || it.yaku == YakuType.ChurenPoto9 }
        assertNull(churenPotoResult, "Should return null when hand lacks 111 or 999")
    }
}
