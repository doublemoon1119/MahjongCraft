package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.domain.base.Meld
import com.doublemoon1119.mahjongcraft.domain.base.MeldType
import com.doublemoon1119.mahjongcraft.domain.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
     * 測試斷么九 - 有副露。
     *
     * 有副露時手牌仍能正確分解為「副露 + 手牌」，並計算斷么九。
     * 此測試驗證 [RiichiHandDecomposer.tryDecomposeStandard] 正確處理副露。
     */
    @Test
    fun `test tanyao with pon fuuro`() {
        // 副露：碰 555m
        // 手牌：234m, 678m, 55m, 自摸 2m = 10張 + 1張 = 14張
        // 手牌與副露全部為 2-8 數牌，應獲得 1 翻（斷么九）
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2)
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 5)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val tanyaoResult = result.yakuResults.find { it.yaku == YakuType.Tanyao }
        assertEquals(1, tanyaoResult?.han, "Tanyao should be 1 han even with fuuro")
    }

    /**
     * 測試一氣通貫。
     *
     * 手牌包含萬子 123、456、789，門前清應獲得 2 翻。
     */
    @Test
    fun `test ittuitsu menzen`() {
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
        assertEquals(2, ittuitsuResult?.han, "Ittuitsu should be 2 han for menzen")
    }

    /**
     * 測試一氣通貫 - 有副露。
     *
     * 有副露時應獲得 1 翻。
     */
    @Test
    fun `test ittuitsu with fuuro`() {
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
        assertEquals(1, ittuitsuResult?.han, "Ittuitsu should be 1 han with fuuro")
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
        assertEquals(3, honitsuResult?.han, "Honitsu should be 3 han for menzen")
    }

    /**
     * 測試混一色 - 有副露。
     *
     * 手牌僅有一種數牌花色 + 字牌，有副露應獲得 2 翻。
     */
    @Test
    fun `test honitsu with fuuro`() {
        // 副露：碰 111m
        // 手牌：234m, 567m, 789m, 東風對子
        val hand = createHand(
            listOf(
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
                Tile.Honor.East
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 9)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val honitsuResult = result.yakuResults.find { it.yaku == YakuType.Honitsu }
        assertEquals(2, honitsuResult?.han, "Honitsu should be 2 han with fuuro")
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
        assertEquals(6, chinitsuResult?.han, "Chinitsu should be 6 han for menzen")
    }

    /**
     * 測試清一色 - 有副露。
     *
     * 手牌僅有一種數牌花色（無字牌），有副露應獲得 5 翻。
     */
    @Test
    fun `test chinitsu with fuuro`() {
        // 副露：碰 111m
        // 手牌：234m, 567m, 789m, 55m
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5)
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 9)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val chinitsuResult = result.yakuResults.find { it.yaku == YakuType.Chinitsu }
        assertEquals(5, chinitsuResult?.han, "Chinitsu should be 5 han with fuuro")
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
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
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
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
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
                Tile.Numeric(Tile.Suit.Dot, 2)
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
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
                Tile.Numeric(Tile.Suit.Character, 7)
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
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

    /**
     * 測試平和 - 門前清兩面聽。
     *
     * 手牌為標準平和型，應獲得 1 翻。
     */
    @Test
    fun `test pinfu menzen ryanmen`() {
        // 手牌：123m, 456m, 789m, 23m, 55m（兩面聽牌：1m, 4m）
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
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5)
            )
        )
        // 自摸 4m，形成 234m 順子
        val winningTile = Tile.Numeric(Tile.Suit.Character, 4)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val pinfuResult = result.yakuResults.find { it.yaku == YakuType.Pinfu }
        assertEquals(1, pinfuResult?.han, "Pinfu should be 1 han")
    }

    /**
     * 測試平和 - 邊張聽牌不應有平和。
     *
     * 聽牌為邊張時，無法獲得平和。
     */
    @Test
    fun `test pinfu with penchan returns null`() {
        // 手牌：123m, 456m, 789m, 12m, 55m（邊張聽牌：3m）
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
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5)
            )
        )
        // 自摸 3m，形成 123m 順子，邊張聽牌（非兩面）
        val winningTile = Tile.Numeric(Tile.Suit.Character, 3)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val pinfuResult = result.yakuResults.find { it.yaku == YakuType.Pinfu }
        assertNull(pinfuResult, "Pinfu should not be present with penchan tenpai")
    }

    /**
     * 測試平和 - 有副露。
     *
     * 有副露時應無法獲得平和。
     */
    @Test
    fun `test pinfu with fuuro returns null`() {
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
                Tile.Numeric(Tile.Suit.Dot, 2)
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 1))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val pinfuResult = result.yakuResults.find { it.yaku == YakuType.Pinfu }
        assertNull(pinfuResult, "Pinfu should not be present when there is fuuro")
    }

    /**
     * 測試平和 - 雀頭是役牌。
     *
     * 雀頭為字牌時，應不成立平和。
     */
    @Test
    fun `test pinfu with yakuhai pair returns null`() {
        // 手牌：123m x2 + 456m + 789m + 東風對子（役牌雀頭）
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
                Tile.Honor.East
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val pinfuResult = result.yakuResults.find { it.yaku == YakuType.Pinfu }
        assertNull(pinfuResult, "Pinfu should not be present with yakuhai pair")
    }

    /**
     * 測試平和 - 單騎聽不成立。
     *
     * 單騎聽不符合平和的兩面聽條件。
     */
    @Test
    fun `test pinfu with tanka wait returns null`() {
        // 手牌：111m, 123m, 456m, 789m, 55m - 單騎聽 5m
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
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
                Tile.Numeric(Tile.Suit.Dot, 5)
            )
        )
        // 單騎聽 5m
        val winningTile = Tile.Numeric(Tile.Suit.Dot, 5)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val pinfuResult = result.yakuResults.find { it.yaku == YakuType.Pinfu }
        assertNull(pinfuResult, "Pinfu should not be present with tanka (single tile) wait")
    }

    /**
     * 測試平和與七對子互斥。
     *
     * 七對子與平和不可能同時成立（七對子需要 7 個對子，平和需要 4 個順子）。
     */
    @Test
    fun `test pinfu and chiitoitsu are mutually exclusive`() {
        // 七對子牌型：7 個對子
        val chiitoitsuHand = createHand(
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

        val context = createContext(chiitoitsuHand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val chiitoitsuResult = result.yakuResults.find { it.yaku == YakuType.Chiitoitsu }
        val pinfuResult = result.yakuResults.find { it.yaku == YakuType.Pinfu }

        assertNotNull(chiitoitsuResult, "Chiitoitsu should be present")
        assertNull(pinfuResult, "Pinfu should not be present with Chiitoitsu hand")
    }

    /**
     * 測試對對胡 - 純手牌（門前清）。
     *
     * 手牌為四組刻子 + 一組雀頭，應獲得 2 翻。
     */
    @Test
    fun `test toitoi menzen`() {
        // 手牌：111m, 222m, 333p, 444s, 77z
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Honor.Red
            )
        )
        val winningTile = Tile.Honor.Red

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val toitoiResult = result.yakuResults.find { it.yaku == YakuType.Toitoi }
        assertEquals(2, toitoiResult?.han, "Toitoi should be 2 han")
    }

    /**
     * 測試對對胡 - 有副露。
     *
     * 有副露時仍可成立對對胡，此為與七對子之差異。
     */
    @Test
    fun `test toitoi with fuuro`() {
        // 副露：碰 111m (1 組刻子)
        // 手牌：222m, 333p, 444s, 77z (3 組刻子 + 1 雀頭)
        // 手牌 10 張 + 副露 3 張 + 自摸 1 張 = 14 張
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Honor.Red
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Honor.Red

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val toitoiResult = result.yakuResults.find { it.yaku == YakuType.Toitoi }
        assertEquals(2, toitoiResult?.han, "Toitoi should be 2 han even with fuuro")
    }

    /**
     * 測試對對胡 - 包含槓。
     *
     * 手牌包含明槓或暗槓時，仍可成立對對胡。
     */
    @Test
    fun `test toitoi with kan`() {
        // 副露：明槓 111m (1 組槓)
        // 手牌：222m, 333p, 444s, 77z (3 組刻子 + 1 雀頭)
        // 手牌 10 張 + 副露 4 張 + 自摸 1 張 = 15 張（含槓多一張）
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Honor.Red
            ),
            melds = listOf(
                Meld(
                    type = MeldType.OPEN_KAN,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Honor.Red

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val toitoiResult = result.yakuResults.find { it.yaku == YakuType.Toitoi }
        assertEquals(2, toitoiResult?.han, "Toitoi should be 2 han with kan")
    }

    /**
     * 測試對對胡 - 含有順子。
     *
     * 手牌含有順子時，應不成立對對胡。
     */
    @Test
    fun `test toitoi with shuntsu returns null`() {
        // 手牌：123m (順子), 111m, 222p, 333s, 77z
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Honor.Red
            )
        )
        val winningTile = Tile.Honor.Red

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val toitoiResult = result.yakuResults.find { it.yaku == YakuType.Toitoi }
        assertNull(toitoiResult, "Should not have Toitoi with shuntsu")
    }

    /**
     * 測試對對胡 - 不足四組刻子。
     *
     * 刻子數量不足四組時，應不成立對對胡。
     */
    @Test
    fun `test toitoi with only three pungs returns null`() {
        // 手牌：111m, 222p, 77z, 123m (順子), 456s (順子)
        // 僅有 2 組刻子
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Honor.Red
            )
        )
        val winningTile = Tile.Honor.Red

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val toitoiResult = result.yakuResults.find { it.yaku == YakuType.Toitoi }
        assertNull(toitoiResult, "Should not have Toitoi with only 2 pungs")
    }

    /**
     * 測試對對胡與七對子互斥。
     *
     * 對對胡與七對子同為 2 翻，但七對子優先計算，對對胡應不成立。
     */
    @Test
    fun `test toitoi and chiitoitsu are mutually exclusive`() {
        // 七對子牌型：7 個對子
        val chiitoitsuHand = createHand(
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

        val context = createContext(chiitoitsuHand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val chiitoitsuResult = result.yakuResults.find { it.yaku == YakuType.Chiitoitsu }
        val toitoiResult = result.yakuResults.find { it.yaku == YakuType.Toitoi }

        assertNotNull(chiitoitsuResult, "Chiitoitsu should be present")
        assertNull(toitoiResult, "Toitoi should not be present with Chiitoitsu hand")
    }

    /**
     * 測試三暗刻 - 門前清三暗刻。
     *
     * 手牌有三組暗刻，應獲得 2 翻。
     */
    @Test
    fun `test sanankou menzen`() {
        // 手牌：111m (暗刻), 222p (暗刻), 333s (暗刻), 77z, 7m (自摸)
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 7)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 7)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val sanankouResult = result.yakuResults.find { it.yaku == YakuType.Sanankou }
        assertEquals(2, sanankouResult?.han, "Sanankou should be 2 han")
    }

    /**
     * 測試三暗刻 - 有副露。
     *
     * 有副露時，手牌中仍需有三組暗刻，應獲得 2 翻。
     */
    @Test
    fun `test sanankou with fuuro`() {
        // 副露：碰 111m
        // 手牌：222p (暗刻), 333s (暗刻), 77z, 5s, 5s (湊成另一暗刻)
        // 手牌 10 張 + 副露 3 張 + 自摸 1 張 = 14 張
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 5)
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 5)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val sanankouResult = result.yakuResults.find { it.yaku == YakuType.Sanankou }
        assertEquals(2, sanankouResult?.han, "Sanankou should be 2 han even with fuuro")
    }

    /**
     * 測試三暗刻 - 含有暗槓。
     *
     * 暗槓也視為暗面子，應成立三暗刻。
     */
    @Test
    fun `test sanankou with ankan`() {
        // 副露：暗槓 111m
        // 手牌：222p (暗刻), 333s (暗刻), 77z, 66z (自摸湊成暗刻)
        // 暗槓 + 2 暗刻 + 1 暗刻（自摸）= 3 暗面子
        // 手牌 10 張 + 暗槓 4 張 + 自摸 1 張 = 15 張（含槓多一張）
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Green,
                Tile.Honor.Green
            ),
            melds = listOf(
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))
                    ),
                    sourceDirection = RelativeDirection.Across
                )
            )
        )
        val winningTile = Tile.Honor.Green

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val sanankouResult = result.yakuResults.find { it.yaku == YakuType.Sanankou }
        assertEquals(2, sanankouResult?.han, "Sanankou should be 2 han with ankan")
    }

    /**
     * 測試三暗刻 - 不足三組暗刻。
     *
     * 手牌中暗刻不足三組時，應不成立三暗刻。
     */
    @Test
    fun `test sanankou with only two ankou returns null`() {
        // 手牌：111m (暗刻), 222p (暗刻), 123s (順子), 77z, 4s (自摸)
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Numeric(Tile.Suit.Bamboo, 4)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 4)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val sanankouResult = result.yakuResults.find { it.yaku == YakuType.Sanankou }
        assertNull(sanankouResult, "Should not have Sanankou with only 2 ankou")
    }

    /**
     * 測試三暗刻 - 含有順子。
     *
     * 含有順子時，只要有三組暗刻仍可成立三暗刻。
     */
    @Test
    fun `test sanankou with shuntsu`() {
        // 手牌：111m (暗刻), 222p (暗刻), 333s (暗刻), 456p (順子), 7z (1 張), 自摸 7z
        // 湊成：3 暗刻 + 1 順子 + 1 雀頭
        // 13 張手牌 (3+3+3+3+1) + 1 張自摸 = 14 張
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 6),
                Tile.Honor.Red
            )
        )
        val winningTile = Tile.Honor.Red

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val sanankouResult = result.yakuResults.find { it.yaku == YakuType.Sanankou }
        assertEquals(2, sanankouResult?.han, "Sanankou should be 2 han even with shuntsu")
    }

    /**
     * 測試三暗刻與對對胡。
     *
     * 對對胡為四組刻子/槓，三暗刻為三組暗刻，兩者可同時成立。
     */
    @Test
    fun `test sanankou and toitoi can coexist`() {
        // 手牌：111m (暗刻), 222p (暗刻), 333s (暗刻), 77z (雀頭), 66z, 6z (暗刻)
        // 111m, 222p, 333s 為暗刻，構成三暗刻
        // 同時也是對對胡（4 組刻子 + 1 雀頭）
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Green,
                Tile.Honor.Green
            )
        )
        val winningTile = Tile.Honor.Green

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val sanankouResult = result.yakuResults.find { it.yaku == YakuType.Sanankou }
        val toitoiResult = result.yakuResults.find { it.yaku == YakuType.Toitoi }

        assertEquals(2, sanankouResult?.han, "Sanankou should be 2 han")
        assertEquals(2, toitoiResult?.han, "Toitoi should be 2 han")
    }

    /**
     * 測試三杠子 - 三個暗槓。
     *
     * 手牌有三個暗槓，應獲得 2 翻。
     */
    @Test
    fun `test sankantsu three ankan`() {
        // 副露：三個暗槓 1111m, 2222p, 3333s
        // 手牌：44m, 77z (雀頭), 自摸 7z
        // 3 個暗槓 + 1 面子 + 1 雀頭 = 三杠子
        // standingTiles 4 張 + winning 1 張 + 3 個暗槓 12 張 = 17 張 (14 + 3)
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Honor.Red,
                Tile.Honor.Red
            ),
            melds = listOf(
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))
                    ),
                    sourceDirection = RelativeDirection.Self
                ),
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2))
                    ),
                    sourceDirection = RelativeDirection.Self
                ),
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 3)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 3)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 3)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 3))
                    ),
                    sourceDirection = RelativeDirection.Self
                )
            )
        )
        val winningTile = Tile.Honor.Red

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val sankantsuResult = result.yakuResults.find { it.yaku == YakuType.Sankantsu }
        assertEquals(2, sankantsuResult?.han, "Sankantsu should be 2 han")
    }

    /**
     * 測試三杠子 - 不足三組槓。
     *
     * 槓數量不足三組時，應不成立三杠子。
     */
    @Test
    fun `test sankantsu with only two kan returns null`() {
        // 副露：兩個暗槓 1111m, 2222p
        // 手牌：333s (面子), 777z (雀頭), 5s (自摸湊成面子)
        // standingTiles 6 張 + winning 1 張 + 2 個槓 8 張 = 15 張 (14 + 1)
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Numeric(Tile.Suit.Bamboo, 5)
            ),
            melds = listOf(
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))
                    ),
                    sourceDirection = RelativeDirection.Self
                ),
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2))
                    ),
                    sourceDirection = RelativeDirection.Self
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 5)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val sankantsuResult = result.yakuResults.find { it.yaku == YakuType.Sankantsu }
        assertNull(sankantsuResult, "Should not have Sankantsu with only 2 kan")
    }

    /**
     * 測試三杠子 - 超過三組槓。
     *
     * 四個槓時成立四杠子 (Sukantsu)，不成立三杠子。
     */
    @Test
    fun `test sankantsu with four kan returns null`() {
        // 副露：四個暗槓 1111m, 2222p, 3333s, 4444s
        // 四杠子是役滿，不應計算三杠子
        // 手牌：7z (自摸湊成雀頭)
        // standingTiles 1 張 + winning 1 張 + 4 個槓 16 張 = 18 張
        val hand = createHand(
            listOf(
                Tile.Honor.Red
            ),
            melds = listOf(
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))
                    ),
                    sourceDirection = RelativeDirection.Self
                ),
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2))
                    ),
                    sourceDirection = RelativeDirection.Self
                ),
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 3)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 3)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 3)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 3))
                    ),
                    sourceDirection = RelativeDirection.Self
                ),
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 4)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 4)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 4)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 4))
                    ),
                    sourceDirection = RelativeDirection.Self
                )
            )
        )
        val winningTile = Tile.Honor.Red

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val sankantsuResult = result.yakuResults.find { it.yaku == YakuType.Sankantsu }
        assertNull(sankantsuResult, "Should not have Sankantsu with 4 kan (should be Sukantsu yakuman)")
    }

    /**
     * 測試三色同刻 - 門前清。
     *
     * 手牌有三個相同數字但不同花色的刻子，應獲得 2 翻。
     */
    @Test
    fun `test sanshoku dokoku menzen`() {
        // 手牌：111m, 111p, 111s, 77z, 56m (自摸)
        // 3 刻子 + 1 雀頭
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 7)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val sanshokuDokokuResult = result.yakuResults.find { it.yaku == YakuType.SanshokuDokoku }
        assertEquals(2, sanshokuDokokuResult?.han, "SanshokuDokoku should be 2 han")
    }

    /**
     * 測試三色同刻 - 有副露。
     *
     * 有副露時仍可成立三色同刻，應獲得 2 翻。
     */
    @Test
    fun `test sanshoku dokoku with fuuro`() {
        // 副露：碰 111m
        // 手牌：111p, 111s, 77z, 66z (自摸湊成刻子)
        // 副露 1 刻子 + 手牌 2 刻子 = 3 刻子
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Green,
                Tile.Honor.Green
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Honor.Green

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val sanshokuDokokuResult = result.yakuResults.find { it.yaku == YakuType.SanshokuDokoku }
        assertEquals(2, sanshokuDokokuResult?.han, "SanshokuDokoku should be 2 han even with fuuro")
    }

    /**
     * 測試三色同刻 - 不足三組。
     *
     * 只有兩組相同數字的刻子時，應不成立三色同刻。
     */
    @Test
    fun `test sanshoku dokoku with only two pungs returns null`() {
        // 手牌：111m, 111p, 234s, 77z, 66s (自摸)
        // 只有 2 組三色同刻的要素
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 6)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 6)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val sanshokuDokokuResult = result.yakuResults.find { it.yaku == YakuType.SanshokuDokoku }
        assertNull(sanshokuDokokuResult, "Should not have SanshokuDokoku with only 2 pungs")
    }
}
