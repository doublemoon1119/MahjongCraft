package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.domain.base.*
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 小四喜與大四喜役種檢測測試。
 *
 * 測試以下役種：
 * - 小四喜 (Shousuushi) - 役滿
 * - 大四喜 (Daisuushii) - 雙倍役滿
 *
 * @see calculateSuushii
 * @see com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandValueCalculator
 */
class SuushiiTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試小四喜 - 南風作為雀頭。
     *
     * 手牌：南、東東東、西西西、北北北、123m（13 張）
     * 胡牌：南
     */
    @Test
    fun `test shousuushi with south pair`() {
        val hand = createHand(
            listOf(
                // 聽牌：南
                Tile.Honor.South,
                // 東刻子
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East,
                // 西刻子
                Tile.Honor.West,
                Tile.Honor.West,
                Tile.Honor.West,
                // 北刻子
                Tile.Honor.North,
                Tile.Honor.North,
                Tile.Honor.North,
                // 順子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3)
            )
        )
        val winningTile = Tile.Honor.South

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertTrue(result.yakuResults.any { it.yaku == YakuType.Shousuushi },
            "Should contain Shousuushi, got: ${result.yakuResults.map { it.yaku }}")
    }

    /**
     * 測試小四喜 - 北風作為雀頭，且含副露。
     *
     * 手牌：北、東東東(副露)、西西西、南南南、123m（13 張）
     * 胡牌：北
     */
    @Test
    fun `test shousuushi with north pair and fuuro`() {
        val hand = createHand(
            listOf(
                // 聽牌：北
                Tile.Honor.North,
                // 南刻子
                Tile.Honor.South,
                Tile.Honor.South,
                Tile.Honor.South,
                // 西刻子
                Tile.Honor.West,
                Tile.Honor.West,
                Tile.Honor.West,
                // 順子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3)
            ),
            melds = listOf(
                // 副露：東刻子
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.East),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.East),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.East)
                    ),
                    sourceDirection = RelativeDirection.Across
                )
            )
        )
        val winningTile = Tile.Honor.North

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertTrue(result.yakuResults.any { it.yaku == YakuType.Shousuushi },
            "Should contain Shousuushi, got: ${result.yakuResults.map { it.yaku }}")
    }

    /**
     * 測試小四喜 - 南風作為雀頭。
     *
     * 手牌：東東東 南南 西西西 11m(雀頭) 234s -> 不是小四喜
     * 胡牌：南
     */
    @Test
    fun `test not shousuushi with three wind kotsu but non-wind pair`() {
        val hand = createHand(
            listOf(
                // 聽牌：南
                Tile.Honor.South,
                Tile.Honor.South,
                // 東刻子
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East,
                // 西刻子
                Tile.Honor.West,
                Tile.Honor.West,
                Tile.Honor.West,
                // 順子
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                // 雀頭
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1)
            )
        )
        val winningTile = Tile.Honor.South

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertFalse(result.isYakuman, "Should not be yakuman")
        assertFalse(result.yakuResults.any { it.yaku == YakuType.Shousuushi },
            "Should not contain Shousuushi, got: ${result.yakuResults.map { it.yaku }}")
    }

    /**
     * 測試大四喜。
     *
     * 手牌：南南、東東東、西西西、北北北、11m（13 張）
     * 胡牌：南
     */
    @Test
    fun `test daisuushi`() {
        val hand = createHand(
            listOf(
                // 聽牌：南
                Tile.Honor.South,
                Tile.Honor.South,
                // 東刻子
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East,
                // 西刻子
                Tile.Honor.West,
                Tile.Honor.West,
                Tile.Honor.West,
                // 北刻子
                Tile.Honor.North,
                Tile.Honor.North,
                Tile.Honor.North,
                // 雀頭
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
            )
        )
        val winningTile = Tile.Honor.South

        val context = createContext(hand, winningTile, isTsumo = false)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertTrue(result.yakuResults.any { it.yaku == YakuType.Daisuushii },
            "Should contain Daisuushii, got: ${result.yakuResults.map { it.yaku }}")
    }

    /**
     * 測試大四喜 - 含副露。
     *
     * 手牌：北北、東東東(副露)、西西西、南南南、11m（13 張）
     * 胡牌：北
     */
    @Test
    fun `test daisuushi with fuuro`() {
        val hand = createHand(
            listOf(
                // 聽牌：北
                Tile.Honor.North,
                Tile.Honor.North,
                // 南刻子
                Tile.Honor.South,
                Tile.Honor.South,
                Tile.Honor.South,
                // 西刻子
                Tile.Honor.West,
                Tile.Honor.West,
                Tile.Honor.West,
                // 雀頭
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1)
            ),
            melds = listOf(
                // 副露：東刻子
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.East),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.East),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.East)
                    ),
                    sourceDirection = RelativeDirection.Across
                )
            )
        )
        val winningTile = Tile.Honor.North

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be double yakuman")
        assertTrue(result.yakuResults.any { it.yaku == YakuType.Daisuushii },
            "Should contain Daisuushii, got: ${result.yakuResults.map { it.yaku }}")
    }

    /**
     * 測試大四喜 - 含暗槓。
     *
     * 手牌：北北、東東東東(副露、暗槓)、西西西、南南南、11m（14 張）
     * 胡牌：北
     */
    @Test
    fun `test daisuushi with ankan`() {
        val hand = createHand(
            listOf(
                // 聽牌：北
                Tile.Honor.North,
                Tile.Honor.North,
                // 南刻子
                Tile.Honor.South,
                Tile.Honor.South,
                Tile.Honor.South,
                // 西刻子
                Tile.Honor.West,
                Tile.Honor.West,
                Tile.Honor.West,
                // 雀頭
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1)
            ),
            melds = listOf(
                // 副露：東暗槓
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.East),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.East),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.East),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.East)
                    ),
                    sourceDirection = RelativeDirection.Self
                )
            )
        )
        val winningTile = Tile.Honor.North

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be double yakuman")
        assertTrue(result.yakuResults.any { it.yaku == YakuType.Daisuushii },
            "Should contain Daisuushii, got: ${result.yakuResults.map { it.yaku }}")
    }

    /**
     * 測試雙碰聽牌下的四喜判定。
     *
     * 手牌：東東東、南南南、西西西、北北、11m
     * 1. 胡「北」：應判定為【大四喜】（4風刻 + 1非風雀頭）
     * 2. 胡「1m」：應判定為【小四喜】（3風刻 + 1風雀頭）
     */
    @Test
    fun `test suushii with different winning tiles in shapon wait`() {
        val baseTiles = listOf(
            // 三組風牌刻子
            Tile.Honor.East, Tile.Honor.East, Tile.Honor.East,
            Tile.Honor.South, Tile.Honor.South, Tile.Honor.South,
            Tile.Honor.West, Tile.Honor.West, Tile.Honor.West,
            // 剩下的對子（雙碰聽牌對象）
            Tile.Honor.North, Tile.Honor.North,
            Tile.Numeric(Tile.Suit.Character, 1), Tile.Numeric(Tile.Suit.Character, 1)
        )

        // 情境 1：胡「北」 -> 變成 北刻子 + 1m雀頭 => 大四喜
        val winningNorth = Tile.Honor.North
        val handNorth = createHand(baseTiles)
        val contextNorth = createContext(handNorth, winningNorth, isTsumo = false)
        val resultNorth = calculator.calculate(contextNorth)

        assertTrue(resultNorth.yakuResults.any { it.yaku == YakuType.Daisuushii },
            "Winning North should be Daisuushii, but got: ${resultNorth.yakuResults.map { it.yaku }}")

        // 情境 2：胡「1m」 -> 變成 1m刻子 + 北雀頭 => 小四喜
        val winning1m = Tile.Numeric(Tile.Suit.Character, 1)
        val hand1m = createHand(baseTiles)
        val context1m = createContext(hand1m, winning1m, isTsumo = false)
        val result1m = calculator.calculate(context1m)

        assertTrue(result1m.yakuResults.any { it.yaku == YakuType.Shousuushi },
            "Winning 1m should be Shousuushi, but got: ${result1m.yakuResults.map { it.yaku }}")
    }
}
