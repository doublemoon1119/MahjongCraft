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
 * 綠一色役種檢測測試。
 *
 * 測試以下役種：
 * - 綠一色 (Ryuuuiisou) - 役滿
 *
 * @see calculateRyuuuiisou
 * @see com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueCalculator
 */
class RyuuuiisouTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試綠一色 - 全部為綠牌（標準型）。
     *
     * 手牌：2s 2s 2s、3s 3s 3s、4s 4s 4s、6s 6s、發 發
     * 胡牌：發
     * 應為 綠一色
     */
    @Test
    fun `test ryuuuiisou all green tiles standard`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 刻子：2s 2s 2s
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                // 刻子：3s 3s 3s
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                // 刻子：4s 4s 4s
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                // 對子：6s 6s
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                // 對子：發 發
                Tile.Honor.Green,
                Tile.Honor.Green
            )
        )
        // 胡牌：發
        val winningTile = Tile.Honor.Green

        val result = calculateRyuuuiisou(hand, winningTile)

        assertTrue(result != null, "Should be Ryuuuiisou")
        assertEquals(YakuType.Ryuuuiisou, result.yaku, "Yaku type should be Ryuuuiisou")
        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-1, result.han, "Han should be -1 for Ryuuuiisou")
    }

    /**
     * 測試綠一色 - 含副露。
     *
     * 手牌：2s 2s 2s (副露)、3s 3s 3s、4s 4s 4s、6s 6s、8s 8s
     * 胡牌：6s
     * 應為 綠一色
     */
    @Test
    fun `test ryuuuiisou with fuuro`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 刻子：3s 3s 3s
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                // 刻子：4s 4s 4s
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                // 對子：6s 6s
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                // 對子：8s 8s
                Tile.Numeric(Tile.Suit.Bamboo, 8),
                Tile.Numeric(Tile.Suit.Bamboo, 8)
            ),
            melds = listOf(
                // 副露：2s 2s 2s（碰）
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 2)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 2)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 2))
                    ),
                    sourceDirection = RelativeDirection.Across
                )
            )
        )
        // 胡牌：6s
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 6)

        val result = calculateRyuuuiisou(hand, winningTile)

        assertTrue(result != null, "Should be Ryuuuiisou with fuuro")
        assertEquals(YakuType.Ryuuuiisou, result.yaku, "Yaku type should be Ryuuuiisou")
        assertTrue(result.isYakuman, "Should be yakuman")
    }

    /**
     * 測試綠一色 - 非綠一色（含有非綠牌）。
     *
     * 手牌：2s 2s 2s、3s 3s 3s、4s 4s 4s、6s 6s、1s 1s
     * 胡牌：1s
     * 應為 非綠一色
     */
    @Test
    fun `test non-ryuuuiisou contains non-green tiles returns null`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 刻子：2s 2s 2s
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                // 刻子：3s 3s 3s
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                // 刻子：4s 4s 4s
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                // 對子：6s 6s
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                // 對子：1s 1s (非綠牌)
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 1)

        val result = calculateRyuuuiisou(hand, winningTile)

        assertNull(result, "Should return null when hand contains non-green tiles")
    }

    /**
     * 測試綠一色 - 非綠一色（含有其他字牌）。
     *
     * 手牌：2s 2s 2s、3s 3s 3s、4s 4s 4s、東 東、發 發
     * 胡牌：東
     * 應為 綠一色
     */
    @Test
    fun `test non-ryuuuiisou contains other honor tiles returns null`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 刻子：2s 2s 2s
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                // 刻子：3s 3s 3s
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                // 刻子：4s 4s 4s
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                // 對子：東 東
                Tile.Honor.East,
                Tile.Honor.East,
                // 對子：發 發
                Tile.Honor.Green,
                Tile.Honor.Green
            )
        )
        // 胡牌：東 (非綠牌)
        val winningTile = Tile.Honor.East

        val result = calculateRyuuuiisou(hand, winningTile)

        assertNull(result, "Should return null when winning tile is not green")
    }

    /**
     * 測試綠一色 - 透過 RiichiHandValueCalculator 整合測試。
     */
    @Test
    fun `test ryuuuiisou via calculator`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 刻子：2s 2s 2s
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                // 刻子：3s 3s 3s
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                // 刻子：4s 4s 4s
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                // 對子：6s 6s
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                // 對子：8s 8s
                Tile.Numeric(Tile.Suit.Bamboo, 8),
                Tile.Numeric(Tile.Suit.Bamboo, 8)
            )
        )
        // 胡牌：8s
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 8)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = false)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-1, result.totalHan, "Total han should be -1")
        assertEquals(1, result.yakuResults.size, "Should have 1 yaku result")
        assertEquals(YakuType.Ryuuuiisou, result.yakuResults[0].yaku, "Yaku should be Ryuuuiisou")
    }

    /**
     * 測試綠一色 - 含副露透過 RiichiHandValueCalculator 整合測試。
     */
    @Test
    fun `test ryuuuiisou with fuuro via calculator`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 刻子：3s 3s 3s
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                // 刻子：4s 4s 4s
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                // 對子：6s 6s
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                // 對子：8s 8s
                Tile.Numeric(Tile.Suit.Bamboo, 8),
                Tile.Numeric(Tile.Suit.Bamboo, 8)
            ),
            melds = listOf(
                // 副露：2s 2s 2s（碰）
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 2)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 2)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 2))
                    ),
                    sourceDirection = RelativeDirection.Across
                )
            )
        )
        // 胡牌：6s
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 6)

        val context = FakeRiichiHandValueContextFactory.create(
            hand = hand,
            winningTile = winningTile,
            isTsumo = true,
            isMenzen = false // 有副露
        )
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-1, result.totalHan, "Total han should be -1")
        assertEquals(1, result.yakuResults.size, "Should have 1 yaku result")
        assertEquals(YakuType.Ryuuuiisou, result.yakuResults[0].yaku, "Yaku should be Ryuuuiisou")
    }
}
