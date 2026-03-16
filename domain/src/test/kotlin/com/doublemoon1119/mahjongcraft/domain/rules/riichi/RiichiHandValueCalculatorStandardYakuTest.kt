package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 立直麻將手牌番數計算機之一般役種測試。
 *
 * 測試內容涵蓋斷么九、一氣通貫、混一色、清一色、一杯口、兩杯口、七對子等役種。
 *
 * @see RiichiHandValueCalculator
 */
class RiichiHandValueCalculatorStandardYakuTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試斷么九 - 門前清。
     *
     * 手牌僅有 2-8 數牌，應獲得 1 翻。
     */
    @Test
    fun `test tanyao menzen`() {
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 6),
                Tile.Numeric(Tile.Suit.Dot, 7)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 2)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val tanyaoResult = result.yakuResults.find { it.yaku == YakuType.Tanyao }
        assertEquals(1, tanyaoResult?.han, "Tanyao should be 1 han")
    }

    /**
     * 測試斷么九 - 有么九牌。
     *
     * 手牌包含 1m，應無法獲得斷么九。
     */
    @Test
    fun `test tanyao with terminal`() {
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 6),
                Tile.Numeric(Tile.Suit.Dot, 7)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 2)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val tanyaoResult = result.yakuResults.find { it.yaku == YakuType.Tanyao }
        assertNull(tanyaoResult, "Should not have Tanyao with terminal tile")
    }

    /**
     * 測試斷么九 - 有字牌。
     *
     * 手牌包含字牌，應無法獲得斷么九。
     */
    @Test
    fun `test tanyao with honor`() {
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Honor.East,
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 6)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 2)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val tanyaoResult = result.yakuResults.find { it.yaku == YakuType.Tanyao }
        assertNull(tanyaoResult, "Should not have Tanyao with honor tile")
    }

    /**
     * 測試一氣通貫。
     *
     * 手牌包含萬子 123、456、789，應獲得 1 翻。
     */
    @Test
    fun `test ittuitsu`() {
        val hand = createHand(
            listOf(
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
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val ittuitsuResult = result.yakuResults.find { it.yaku == YakuType.Ittuitsu }
        assertEquals(1, ittuitsuResult?.han, "Ittuitsu should be 1 han")
    }

    /**
     * 測試一氣通貫 - 有副露。
     *
     * 有副露時應無法獲得一氣通貫。
     */
    @Test
    fun `test ittuitsu with meld`() {
        val hand = createHand(
            listOf(
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
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val ittuitsuResult = result.yakuResults.find { it.yaku == YakuType.Ittuitsu }
        assertNull(ittuitsuResult, "Should not have Ittuitsu with meld")
    }

    /**
     * 測試混一色。
     *
     * 手牌僅有一種數牌花色 + 字牌，應獲得 3 翻。
     */
    @Test
    fun `test honitsu`() {
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        val honitsuResult = result.yakuResults.find { it.yaku == YakuType.Honitsu }
        assertEquals(3, honitsuResult?.han, "Honitsu should be 3 han")
    }

    /**
     * 測試清一色。
     *
     * 手牌僅有一種數牌花色（無字牌），應獲得 6 翻。
     */
    @Test
    fun `test chinitsu`() {
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 2)

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        val chinitsuResult = result.yakuResults.find { it.yaku == YakuType.Chinitsu }
        assertEquals(6, chinitsuResult?.han, "Chinitsu should be 6 han")
    }

    /**
     * 測試清一色與混一色互斥。
     *
     * 清一手牌應只計算清一色（6 翻），不計算混一色（3 翻）。
     */
    @Test
    fun `test chinitsu overrides honitsu`() {
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 2)

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        val chinitsuResult = result.yakuResults.find { it.yaku == YakuType.Chinitsu }
        val honitsuResult = result.yakuResults.find { it.yaku == YakuType.Honitsu }

        assertEquals(6, chinitsuResult?.han, "Chinitsu should be 6 han")
        assertNull(honitsuResult, "Honitsu should not be present when Chinitsu is present")
    }

    /**
     * 測試七對子 - 門前清。
     *
     * 手牌為七個對子，應獲得 2 翻。
     */
    @Test
    fun `test chiitoitsu menzen`() {
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Dot, 7)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Dot, 7)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val chiitoitsuResult = result.yakuResults.find { it.yaku == YakuType.Chiitoitsu }
        assertEquals(2, chiitoitsuResult?.han, "Chiitoitsu should be 2 han for menzen")
    }

    /**
     * 測試七對子 - 有副露（非門前清）。
     *
     * 七對子必須為門前清，有副露時應不成立。
     */
    @Test
    fun `test chiitoitsu with fuuro returns null`() {
        // 七對子手牌：13 張手牌 + 1 張自摸 = 7 對子
        // 其中一對被副露消耗（副露 3 張 + 手牌 10 張 + 摸牌 1 張 = 14 張）
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Dot, 7),
                Tile.Numeric(Tile.Suit.Dot, 7)
            ),
            hasExposedMelds = true
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val chiitoitsuResult = result.yakuResults.find { it.yaku == YakuType.Chiitoitsu }
        assertNull(chiitoitsuResult, "Chiitoitsu should not be present when there is fuuro")
    }

    /**
     * 測試七對子 - 非七對子牌型。
     *
     * 一般手牌應不成立七對子役種。
     */
    @Test
    fun `test non-chiitoitsu hand returns null`() {
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 7),
                Tile.Numeric(Tile.Suit.Dot, 8),
                Tile.Numeric(Tile.Suit.Dot, 9)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val chiitoitsuResult = result.yakuResults.find { it.yaku == YakuType.Chiitoitsu }
        assertNull(chiitoitsuResult, "Non-chiitoitsu hand should not have chiitoitsu yaku")
    }

    /**
     * 測試一杯口 - 門前清。
     *
     * 手牌有兩個相同的順子（123m 兩個），應獲得 1 翻。
     */
    @Test
    fun `test iipeikou menzen`() {
        // 手牌 13 張：兩個 123m 順子 + 一個 456m 順子 + 雀頭 77m
        // 123m x2 (6) + 456m (3) + 77m (2) + 1m (自摸) = 12 張 -> 需要再調整
        // 正確：123m x2 (6) + 456m (3) + 789m (3) + 77m = 13 張手牌
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 7)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 7)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val iipeikouResult = result.yakuResults.find { it.yaku == YakuType.Iipeikou }
        assertEquals(1, iipeikouResult?.han, "Iipeikou should be 1 han")
    }

    /**
     * 測試一杯口 - 有副露。
     *
     * 有副露時應無法獲得一杯口。
     */
    @Test
    fun `test iipeikou with fuuro returns null`() {
        // 手牌 13 張：兩個 123m 順子 + 一個 456m 順子 + 雀頭 55m
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 5)
            ),
            hasExposedMelds = true
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 7)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val iipeikouResult = result.yakuResults.find { it.yaku == YakuType.Iipeikou }
        assertNull(iipeikouResult, "Iipeikou should not be present when there is fuuro")
    }

    /**
     * 測試一杯口 - 非一杯口牌型。
     *
     * 手牌中沒有兩個相同的順子，應不成立一杯口。
     */
    @Test
    fun `test non-iipeikou hand returns null`() {
        val hand = createHand(
            listOf(
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
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val iipeikouResult = result.yakuResults.find { it.yaku == YakuType.Iipeikou }
        assertNull(iipeikouResult, "Non-iipeikou hand should not have iipeikou yaku")
    }

    /**
     * 測試兩杯口 - 門前清。
     *
     * 手牌有兩個不同的相同順子（123m 兩個 + 456m 兩個），應獲得 3 翻。
     */
    @Test
    fun `test ryanpeikou menzen`() {
        // 手牌 13 張：兩個 123m 順子 + 兩個 456m 順子 + 雀頭 77m
        // 123m x2 (6) + 456m x2 (6) + 77m (2) = 14 tiles total
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 7)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val ryanpeikouResult = result.yakuResults.find { it.yaku == YakuType.Ryanpeikou }
        assertEquals(3, ryanpeikouResult?.han, "Ryanpeikou should be 3 han")
    }

    /**
     * 測試兩杯口 - 有副露。
     *
     * 有副露時應無法獲得兩杯口。
     */
    @Test
    fun `test ryanpeikou with fuuro returns null`() {
        // 手牌 13 張：兩個 123m 順子 + 兩個 456m 順子 + 雀頭 77m
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7)
            ),
            hasExposedMelds = true
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 7)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val ryanpeikouResult = result.yakuResults.find { it.yaku == YakuType.Ryanpeikou }
        assertNull(ryanpeikouResult, "Ryanpeikou should not be present when there is fuuro")
    }

    /**
     * 測試兩杯口優先於七對子。
     *
     * 兩杯口 (3 翻) 優先於七對子 (2 翻)。
     */
    @Test
    fun `test ryanpeikou takes precedence over chiitoitsu`() {
        // 手牌 13 張：兩個 123m 順子 + 兩個 456m 順子 + 雀頭 77m
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 7)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val ryanpeikouResult = result.yakuResults.find { it.yaku == YakuType.Ryanpeikou }
        val chiitoitsuResult = result.yakuResults.find { it.yaku == YakuType.Chiitoitsu }

        assertEquals(3, ryanpeikouResult?.han, "Ryanpeikou should be 3 han")
        assertNull(chiitoitsuResult, "Chiitoitsu should not be present when Ryanpeikou is present")
    }

    /**
     * 測試七對子優先於一杯口。
     *
     * 七對子 (2 翻) 優先於一杯口 (1 翻)。
     */
    @Test
    fun `test chiitoitsu takes precedence over iipeikou`() {
        // 這個牌型同時滿足七對子和一杯口的條件，但七對子優先
        // 11223344556677m -> 7 個對子
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 7)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 7)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val chiitoitsuResult = result.yakuResults.find { it.yaku == YakuType.Chiitoitsu }
        val iipeikouResult = result.yakuResults.find { it.yaku == YakuType.Iipeikou }

        assertEquals(2, chiitoitsuResult?.han, "Chiitoitsu should be 2 han")
        assertNull(iipeikouResult, "Iipeikou should not be present when Chiitoitsu is present")
    }
}
