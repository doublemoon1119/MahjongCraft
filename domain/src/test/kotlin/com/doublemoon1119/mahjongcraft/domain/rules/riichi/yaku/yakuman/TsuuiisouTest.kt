package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.domain.base.Meld
import com.doublemoon1119.mahjongcraft.domain.base.MeldType
import com.doublemoon1119.mahjongcraft.domain.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.testing.domain.fakes.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.domain.fakes.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.domain.fakes.rules.riichi.FakeRiichiHandValueContextFactory
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 字一色役種檢測測試。
 *
 * 測試以下役種：
 * - 字一色 (Tsuuiisou) - 役滿
 *
 * @see calculateTsuuiisou
 * @see com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandValueCalculator
 */
class TsuuiisouTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試字一色 - 全部為字牌。
     *
     * 手牌：東東東、南南南、西西西、北北北、白
     * 胡牌：白
     * 應為 大四喜 + 四暗刻單騎 + 字一色
     */
    @Test
    fun `test tsuuiisou all honor tiles`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 刻子：東東東
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East,
                // 刻子：南南南
                Tile.Honor.South,
                Tile.Honor.South,
                Tile.Honor.South,
                // 刻子：西西西
                Tile.Honor.West,
                Tile.Honor.West,
                Tile.Honor.West,
                // 刻子：北北北
                Tile.Honor.North,
                Tile.Honor.North,
                Tile.Honor.North,
                // 單張：白
                Tile.Honor.White
            )
        )
        // Winning: 白
        val winningTile = Tile.Honor.White

        val result = calculateTsuuiisou(hand, winningTile)

        assertTrue(result != null, "Should be Tsuuiisou")
        assertEquals(YakuType.Tsuuiisou, result.yaku, "Yaku type should be Tsuuiisou")
        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-1, result.han, "Han should be -1 for Tsuuiisou")
    }

    /**
     * 測試字一色 - 含副露。
     *
     * 手牌：東東東 (副露)、南南南、西西西、北北北、發
     * 胡牌：發
     * 應為 四暗刻單騎 + 字一色
     */
    @Test
    fun `test tsuuiisou with fuuro`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 刻子：南南南
                Tile.Honor.South,
                Tile.Honor.South,
                Tile.Honor.South,
                // 刻子：西西西
                Tile.Honor.West,
                Tile.Honor.West,
                Tile.Honor.West,
                // 刻子：北北北
                Tile.Honor.North,
                Tile.Honor.North,
                Tile.Honor.North,
                // 單張：發
                Tile.Honor.Green
            ),
            melds = listOf(
                // 副露：東東東（碰）
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Honor.East),
                        FakeIdentifiedTileFactory.create(Tile.Honor.East),
                        FakeIdentifiedTileFactory.create(Tile.Honor.East)
                    ),
                    sourceDirection = RelativeDirection.Across
                )
            )
        )
        // Winning: 發
        val winningTile = Tile.Honor.Green

        val result = calculateTsuuiisou(hand, winningTile)

        assertTrue(result != null, "Should be Tsuuiisou with fuuro")
        assertEquals(YakuType.Tsuuiisou, result.yaku, "Yaku type should be Tsuuiisou")
        assertTrue(result.isYakuman, "Should be yakuman")
    }

    /**
     * 測試字一色 - 非字一色（含有數牌）。
     *
     * 手牌：東東東、南南南、1m 1m 1m、白白白、中
     * 胡牌：中
     * 應為 四暗刻單騎（非字一色）
     */
    @Test
    fun `test non-tsuuiisou returns null`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 刻子：東東東
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East,
                // 刻子：南南南
                Tile.Honor.South,
                Tile.Honor.South,
                Tile.Honor.South,
                // 刻子：1m 1m 1m（數牌！）
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                // 對子：白白
                Tile.Honor.White,
                Tile.Honor.White,
                Tile.Honor.White,
                // 對子：中
                Tile.Honor.Red
            )
        )
        val winningTile = Tile.Honor.Red

        val result = calculateTsuuiisou(hand, winningTile)

        assertNull(result, "Should return null when hand contains numeric tiles")
    }

    /**
     * 測試字一色 - 透過 RiichiHandValueCalculator 整合測試。
     */
    @Test
    fun `test tsuuiisou via calculator`() {
        // 手牌: 七對子字一色
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.South,
                Tile.Honor.South,
                Tile.Honor.West,
                Tile.Honor.West,
                Tile.Honor.North,
                Tile.Honor.North,
                Tile.Honor.White,
                Tile.Honor.White,
                Tile.Honor.Green,
                Tile.Honor.Green,
                Tile.Honor.Red
            )
        )
        // Winning: 中
        val winningTile = Tile.Honor.Red

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-1, result.totalHan, "Total han should be -1")
        assertEquals(1, result.yakuResults.size, "Should have 1 yaku result")
        assertEquals(YakuType.Tsuuiisou, result.yakuResults[0].yaku, "Yaku should be Tsuuiisou")
    }

    /**
     * 測試字一色 - 含副露透過 RiichiHandValueCalculator 整合測試。
     *
     * 此手牌同時滿足字一色 (Tsuuiisou) 和大四喜 (Daisuushi) 的條件，
     * 因此總番數為 -3（三倍役滿）。
     */
    @Test
    fun `test tsuuiisou with fuuro via calculator`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 刻子：南南南
                Tile.Honor.South,
                Tile.Honor.South,
                Tile.Honor.South,
                // 刻子：西西西
                Tile.Honor.West,
                Tile.Honor.West,
                Tile.Honor.West,
                // 刻子：北北北
                Tile.Honor.North,
                Tile.Honor.North,
                Tile.Honor.North,
                // 單張：發
                Tile.Honor.Green
            ),
            melds = listOf(
                // 副露：東東東（碰）
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Honor.East),
                        FakeIdentifiedTileFactory.create(Tile.Honor.East),
                        FakeIdentifiedTileFactory.create(Tile.Honor.East)
                    ),
                    sourceDirection = RelativeDirection.Across
                )
            )
        )
        // Winning: 發
        val winningTile = Tile.Honor.Green

        val context = FakeRiichiHandValueContextFactory.create(
            hand = hand,
            winningTile = winningTile,
            isTsumo = true,
            isMenzen = false // 有副露
        )
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-3, result.totalHan, "Total han should be -3")
        assertEquals(2, result.yakuResults.size, "Should have 2 yaku results")
        assertEquals(YakuType.Tsuuiisou, result.yakuResults[0].yaku, "Yaku should be Tsuuiisou")
        assertEquals(YakuType.Daisuushii, result.yakuResults[1].yaku, "Yaku should be Daisuushi")
    }
}
