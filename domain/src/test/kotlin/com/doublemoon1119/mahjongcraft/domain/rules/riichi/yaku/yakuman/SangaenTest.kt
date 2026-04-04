package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.domain.base.*
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import java.util.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 小三元與大三元役種檢測測試。
 *
 * 測試以下役種：
 * - 小三元 (Shousangen) - 2 番
 * - 大三元 (Daisangen) - 役滿
 *
 * @see calculateSangaen
 * @see com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandValueCalculator
 */
class SangaenTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試小三元 - 紅中作為雀頭。
     *
     * 手牌：發發發、白白白、123m、456p、中
     * 胡牌：中
     */
    @Test
    fun `test shousangen with red pair`() {
        val hand = createHand(
            listOf(
                // 聽牌：中
                Tile.Honor.Red,
                // 發刻子
                Tile.Honor.Green,
                Tile.Honor.Green,
                Tile.Honor.Green,
                // 白刻子
                Tile.Honor.White,
                Tile.Honor.White,
                Tile.Honor.White,
                // 順子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                // 順子
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 6)
            )
        )
        val winningTile = Tile.Honor.Red

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertFalse(result.isYakuman, "Should not be yakuman")
        assertTrue(
            result.yakuResults.any { it.yaku == YakuType.Shousangen },
            "Should contain Shousangen"
        )
    }

    /**
     * 測試小三元 - 發財作為雀頭，且含副露。
     *
     * 手牌：白白白(副露)、中中中、123m、56p、發
     * 胡牌：發
     */
    @Test
    fun `test shousangen with green pair and fuuro`() {
        val hand = createHand(
            listOf(
                // 聽牌：發
                Tile.Honor.Green,
                // 中刻子
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Red,
                // 順子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                // 順子
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 6)
            ),
            melds = listOf(
                // 副露：白刻子
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.White),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.White),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.White)
                    ),
                    sourceDirection = RelativeDirection.Across
                )
            )
        )
        val winningTile = Tile.Honor.Green

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertFalse(result.isYakuman, "Should not be yakuman")
        assertTrue(
            result.yakuResults.any { it.yaku == YakuType.Shousangen },
            "Should contain Shousangen"
        )
    }

    /**
     * 測試小三元 - 白板暗槓。
     *
     * 手牌：白白白白(副露)、中中中、123m、56p、發
     * 胡牌：發
     */
    @Test
    fun `test shousangen with ankan`() {
        val hand = createHand(
            listOf(
                // 聽牌：發
                Tile.Honor.Green,
                // 中刻子
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Red,
                // 順子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                // 順子
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 6)
            ),
            melds = listOf(
                // 暗槓：白
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.White),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.White),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.White),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.White)
                    ),
                    sourceDirection = RelativeDirection.Self
                )
            )
        )
        val winningTile = Tile.Honor.Green

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertFalse(result.isYakuman, "Should not be yakuman")
        assertTrue(
            result.yakuResults.any { it.yaku == YakuType.Shousangen },
            "Should contain Shousangen"
        )
    }

    /**
     * 測試不是小三元 - 三個三元牌刻子但非三元牌雀頭。
     *
     * 手牌：中中中、發發、123m、白白白、11m
     * 胡牌：發
     *
     * 此手牌應為大三元而非小三元。
     */
    @Test
    fun `test not shousangen with three dragon kotsu and non-dragon pair`() {
        val hand = createHand(
            listOf(
                // 聽牌：發
                Tile.Honor.Green,
                Tile.Honor.Green,
                // 中刻子
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Red,
                // 白刻子
                Tile.Honor.White,
                Tile.Honor.White,
                Tile.Honor.White,
                // 順子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                // 雀頭（數牌）
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1)
            )
        )
        val winningTile = Tile.Honor.Green

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        // 三個三元牌刻子應該是大三元
        assertTrue(result.isYakuman, "Should be yakuman")
        assertTrue(
            result.yakuResults.any { it.yaku == YakuType.Daisangen },
            "Should contain Daisangen, got: ${result.yakuResults.map { it.yaku }}"
        )
        assertFalse(
            result.yakuResults.any { it.yaku == YakuType.Shousangen },
            "Should not contain Shousangen"
        )
    }

    /**
     * 測試大三元。
     *
     * 手牌：中中、發發發、白白白、123m、55p
     * 胡牌：中
     */
    @Test
    fun `test daisangen`() {
        val hand = createHand(
            listOf(
                // 聽牌：中
                Tile.Honor.Red,
                Tile.Honor.Red,
                // 發刻子
                Tile.Honor.Green,
                Tile.Honor.Green,
                Tile.Honor.Green,
                // 白刻子
                Tile.Honor.White,
                Tile.Honor.White,
                Tile.Honor.White,
                // 順子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                // 雀頭
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 5)
            )
        )
        val winningTile = Tile.Honor.Red

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertTrue(
            result.yakuResults.any { it.yaku == YakuType.Daisangen },
            "Should contain Daisangen, got: ${result.yakuResults.map { it.yaku }}"
        )
    }

    /**
     * 測試大三元 - 含副露。
     *
     * 手牌：發發、白白白(副露)、中中中、123m、55p
     * 胡牌：發
     */
    @Test
    fun `test daisangen with fuuro`() {
        val hand = createHand(
            listOf(
                // 聽牌：發
                Tile.Honor.Green,
                Tile.Honor.Green,
                // 中刻子
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Red,
                // 順子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                // 雀頭
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 5)
            ),
            melds = listOf(
                // 副露：白刻子
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.White),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.White),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.White)
                    ),
                    sourceDirection = RelativeDirection.Across
                )
            )
        )
        val winningTile = Tile.Honor.Green

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertTrue(
            result.yakuResults.any { it.yaku == YakuType.Daisangen },
            "Should contain Daisangen, got: ${result.yakuResults.map { it.yaku }}"
        )
    }

    /**
     * 測試大三元 - 含大明槓。
     *
     * 手牌：發發、白白白(大明槓)、中中中、123m、55p
     * 胡牌：發
     */
    @Test
    fun `test daisangen with minkan`() {
        val hand = createHand(
            listOf(
                // 聽牌：發
                Tile.Honor.Green,
                Tile.Honor.Green,
                // 中刻子
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Red,
                // 順子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                // 雀頭
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 5)
            ),
            melds = listOf(
                // 大明槓：白
                Meld(
                    type = MeldType.OPEN_KAN,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.White),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.White),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.White),
                        IdentifiedTile(UUID.randomUUID(), Tile.Honor.White)
                    ),
                    sourceDirection = RelativeDirection.Across
                )
            )
        )
        val winningTile = Tile.Honor.Green

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertTrue(
            result.yakuResults.any { it.yaku == YakuType.Daisangen },
            "Should contain Daisangen, got: ${result.yakuResults.map { it.yaku }}"
        )
    }

    /**
     * 測試雙碰聽牌下的三元判定。
     *
     * 手牌：中中中、發發發、111p、白白、11m
     * 1. 胡「白」：應判定為【大三元】（3龍刻 + 非龍雀頭）
     * 2. 胡「1m」：應判定為【小三元】（2龍刻 + 1龍雀頭）
     */
    @Test
    fun `test sangaen with different winning tiles in shapon wait`() {
        val baseTiles = listOf(
            // 兩組三元牌刻子
            Tile.Honor.Red, Tile.Honor.Red, Tile.Honor.Red,
            Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
            // 一組任意面子
            Tile.Numeric(Tile.Suit.Dot, 1),
            Tile.Numeric(Tile.Suit.Dot, 1),
            Tile.Numeric(Tile.Suit.Dot, 1),
            // 剩下的對子（雙碰聽牌對象）
            Tile.Honor.White, Tile.Honor.White,
            Tile.Numeric(Tile.Suit.Character, 1), Tile.Numeric(Tile.Suit.Character, 1)
        )

        // 情境 1：胡「白」 -> 變成 白刻子 + 1m雀頭 => 大三元
        val winningWhite = Tile.Honor.White
        val handWhite = createHand(baseTiles)
        val contextWhite = createContext(handWhite, winningWhite, isTsumo = false)
        val resultWhite = calculator.calculate(contextWhite)

        assertTrue(
            resultWhite.yakuResults.any { it.yaku == YakuType.Daisangen },
            "Winning White should be Daisangen, but got: ${resultWhite.yakuResults.map { it.yaku }}"
        )

        // 情境 2：胡「1m」 -> 變成 1m刻子 + 白雀頭 => 小三元
        val winning1m = Tile.Numeric(Tile.Suit.Character, 1)
        val hand1m = createHand(baseTiles)
        val context1m = createContext(hand1m, winning1m, isTsumo = false)
        val result1m = calculator.calculate(context1m)

        assertTrue(
            result1m.yakuResults.any { it.yaku == YakuType.Shousangen },
            "Winning 1m should be Shousangen, but got: ${result1m.yakuResults.map { it.yaku }}"
        )
    }

    /**
     * 測試不符合三元 - 只有兩個三元牌刻子。
     *
     * 手牌：中中中、123m、456p、789s、55p（13 張）
     * 胡牌：中
     *
     * 此手牌應為對對胡而非三元系列。
     */
    @Test
    fun `test not sangaen with only one dragon kotsu`() {
        val hand = createHand(
            listOf(
                // 聽牌：中
                Tile.Honor.Red,
                Tile.Honor.Red,
                // 刻子
                Tile.Honor.Green,
                Tile.Honor.Green,
                Tile.Honor.Green,
                // 刻子
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                // 刻子
                Tile.Numeric(Tile.Suit.Bamboo, 7),
                Tile.Numeric(Tile.Suit.Bamboo, 7),
                Tile.Numeric(Tile.Suit.Bamboo, 7),
                // 雀頭
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 5)
            )
        )
        val winningTile = Tile.Honor.Red

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertFalse(
            result.yakuResults.any { it.yaku == YakuType.Daisangen },
            "Should not contain Daisangen"
        )
        assertFalse(
            result.yakuResults.any { it.yaku == YakuType.Shousangen },
            "Should not contain Shousangen"
        )
    }
}
