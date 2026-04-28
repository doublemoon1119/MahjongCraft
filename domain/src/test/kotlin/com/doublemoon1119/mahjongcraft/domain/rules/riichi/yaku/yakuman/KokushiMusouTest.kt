package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.fakes.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.domain.fakes.rules.riichi.FakeRiichiHandValueContextFactory
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandDecomposer
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.util.withoutRed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 國士無雙役種檢測測試。
 *
 * 測試以下役種：
 * - 國士無雙 (Kokushi Musou) - 役滿
 * - 國士無雙十三面 (Kokushi Musou 13-men) - 雙倍役滿
 *
 * @see calculateKokushiMusou
 * @see com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandValueCalculator
 */
class KokushiMusouTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試國士無雙 - 一般型（單騎聽牌）。
     *
     * 手牌：1m1m 9m 1p 9p 東西南北白發中 (13 張)
     * 胡牌：9s（與 headTile 不同，單騎聽牌）
     * 應為役滿 (Yakuman)
     */
    @Test
    fun `test kokushi musou general`() {
        // Hand: 1m1m 9m 1p 9p 東西南北白發中 (13 tiles) - head = 1m
        val tiles = listOf(
            // 對子：1m (head)
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 1),
            // 單張
            Tile.Numeric(Tile.Suit.Character, 9),
            Tile.Numeric(Tile.Suit.Dot, 1),
            Tile.Numeric(Tile.Suit.Dot, 9),
            Tile.Numeric(Tile.Suit.Bamboo, 1),
            // 字牌
            Tile.Honor.East,
            Tile.Honor.South,
            Tile.Honor.West,
            Tile.Honor.North,
            Tile.Honor.White,
            Tile.Honor.Green,
            Tile.Honor.Red
        )
        // Winning: 9s != headTile(1m) → 一般國士無雙
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 9).withoutRed

        val handTiles = tiles.map { it.withoutRed }
        val handStructures = RiichiHandDecomposer.decompose(handTiles, winningTile)

        val result = handStructures.mapNotNull { handStructure ->
            calculateKokushiMusou(handStructure, winningTile)
        }

        assertTrue(result.isNotEmpty(), "Should be Kokushi Musou")
        assertTrue(result.all { it.yaku == YakuType.KokushiMusou }, "Yaku type should be KokushiMusou")
        assertTrue(result.all { it.isYakuman }, "Should be yakuman")
        assertTrue(result.all { it.han == -1 }, "Han should be -1 for yakuman")
    }

    /**
     * 測試國士無雙十三面（多面聽牌）。
     *
     * 手牌：1m 9m 1p 9p 1s 9s 東西南北白發中 (13 張)
     * 胡牌：1m（與 headTile 相同，十三面）
     * 應為雙倍役滿 (Double Yakuman)
     */
    @Test
    fun `test kokushi musou 13-men`() {
        // Hand: 1m 9m 1p 9p 1s 9s 東西南北白發中 (13 tiles, all different)
        val tiles = listOf(
            // 單張（全部 13 種）
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 9),
            Tile.Numeric(Tile.Suit.Dot, 1),
            Tile.Numeric(Tile.Suit.Dot, 9),
            Tile.Numeric(Tile.Suit.Bamboo, 1),
            Tile.Numeric(Tile.Suit.Bamboo, 9),
            // 字牌
            Tile.Honor.East,
            Tile.Honor.South,
            Tile.Honor.West,
            Tile.Honor.North,
            Tile.Honor.White,
            Tile.Honor.Green,
            Tile.Honor.Red
        )
        // Winning: 1m == headTile(1m) → 十三面
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1).withoutRed

        val handTiles = tiles.map { it.withoutRed }
        val handStructures = RiichiHandDecomposer.decompose(handTiles, winningTile)

        val result = handStructures.mapNotNull { handStructure ->
            calculateKokushiMusou(handStructure, winningTile)
        }

        assertTrue(result.isNotEmpty(), "Should be Kokushi Musou 13-men")
        assertTrue(result.all { it.yaku == YakuType.KokushiMusou13 }, "Yaku type should be KokushiMusou13")
        assertTrue(result.all { it.isDoubleYakuman }, "Should be double yakuman")
        assertTrue(result.all { it.han == -2 }, "Han should be -2 for double yakuman")
    }

    /**
     * 測試國士無雙 - 非國士無雙牌型（應為 null）。
     */
    @Test
    fun `test non-kokushi musou returns null`() {
        // Hand: 標準手牌
        val tiles = listOf(
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Dot, 1),
            Tile.Numeric(Tile.Suit.Dot, 2),
            Tile.Numeric(Tile.Suit.Dot, 3),
            Tile.Numeric(Tile.Suit.Bamboo, 7),
            Tile.Numeric(Tile.Suit.Bamboo, 8),
            Tile.Numeric(Tile.Suit.Bamboo, 9),
            Tile.Honor.East,
            Tile.Honor.East,
            Tile.Honor.East,
            Tile.Honor.White
        )
        val winningTile = Tile.Honor.White

        val handTiles = tiles.map { it.withoutRed }
        val handStructures = RiichiHandDecomposer.decompose(handTiles, winningTile)

        val result = handStructures.mapNotNull { handStructure ->
            calculateKokushiMusou(handStructure, winningTile)
        }

        assertTrue(result.isEmpty(), "Should be empty when hand is not Kokushi Musou")
    }

    /**
     * 測試國士無雙 - 透過 RiichiHandValueCalculator 整合測試（一般型）。
     */
    @Test
    fun `test kokushi musou via calculator`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 對子：1m
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                // 單張
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                // 字牌
                Tile.Honor.East,
                Tile.Honor.South,
                Tile.Honor.West,
                Tile.Honor.North,
                Tile.Honor.White,
                Tile.Honor.Green,
                Tile.Honor.Red
            )
        )
        // Winning: 9s != headTile(1m)
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 9)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-1, result.totalHan, "Total han should be -1")
        assertEquals(1, result.yakuResults.size, "Should have 1 yaku result")
        assertEquals(YakuType.KokushiMusou, result.yakuResults[0].yaku, "Yaku should be KokushiMusou")
    }

    /**
     * 測試國士無雙十三面 - 透過 RiichiHandValueCalculator 整合測試。
     */
    @Test
    fun `test kokushi musou 13-men via calculator`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 單張：全部 13 種
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                // 字牌
                Tile.Honor.East,
                Tile.Honor.South,
                Tile.Honor.West,
                Tile.Honor.North,
                Tile.Honor.White,
                Tile.Honor.Green,
                Tile.Honor.Red
            )
        )
        // Winning: 1m == headTile → 十三面
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-2, result.totalHan, "Total han should be -2 for double yakuman")
        assertEquals(1, result.yakuResults.size, "Should have 1 yaku result")
        assertEquals(YakuType.KokushiMusou13, result.yakuResults[0].yaku, "Yaku should be KokushiMusou13")
    }
}
