package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiYakuContext
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.domain.table.Wind
import java.util.*
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 立直麻將手牌番數計算機之單元測試。
 *
 * 測試內容涵蓋寶牌、立直、一發、嶺上花、海底撈月、河底撈魚、槓槓、搶槓等役種的計算。
 *
 * @see RiichiHandValueCalculator
 */
class RiichiHandValueCalculatorTest {

    private val calculator = RiichiHandValueCalculator()

    private fun createHand(tiles: List<Tile>): Hand {
        val identifiedTiles = tiles.map { IdentifiedTile(UUID.randomUUID(), it) }
        return Hand(identifiedTiles.toMutableList())
    }

    private fun createContext(
        hand: Hand,
        winningTile: Tile,
        isTsumo: Boolean,
        isRiichi: Boolean = false,
        isIppatsu: Boolean = false,
        isDoubleRiichi: Boolean = false,
        isMenzen: Boolean = true,
        doraIndicators: List<Tile> = emptyList(),
        uraDoraIndicators: List<Tile> = emptyList(),
        revealedExposedKans: List<Tile> = emptyList(),
        roundWind: Wind = Wind.EAST,
        seatWind: Wind = Wind.EAST,
        isLastDraw: Boolean = false,
        isLastDiscard: Boolean = false,
        isRobbingKan: Boolean = false,
        isRinshanKaihou: Boolean = false
    ): RiichiYakuContext {
        return RiichiYakuContext(
            hand = hand,
            winningTile = winningTile,
            isTsumo = isTsumo,
            isRiichi = isRiichi,
            isIppatsu = isIppatsu,
            isDoubleRiichi = isDoubleRiichi,
            isMenzen = isMenzen,
            doraIndicators = doraIndicators,
            uraDoraIndicators = uraDoraIndicators,
            revealedExposedKans = revealedExposedKans,
            roundWind = roundWind,
            seatWind = seatWind,
            isLastDraw = isLastDraw,
            isLastDiscard = isLastDiscard,
            isRobbingKan = isRobbingKan,
            isRinshanKaihou = isRinshanKaihou
        )
    }

    /**
     * 測試寶牌計算 - 單一寶牌指示牌。
     *
     * 寶牌指示牌為 5m，手牌包含 6m（立牌），胡牌張也是 6m，應獲得 2 翻。
     */
    @Test
    fun `test dora calculation with single indicator`() {
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 6)
        val doraIndicators = listOf(Tile.Numeric(Tile.Suit.Character, 5))

        val context = createContext(hand, winningTile, isTsumo = true, doraIndicators = doraIndicators)
        val result = calculator.calculate(context)

        val doraResult = result.yakuResults.find { it.yaku == YakuType.Dora }
        assertEquals(2, doraResult?.han, "Should have 2 dora (one in hand + one as winning tile)")
    }

    /**
     * 測試寶牌計算 - 多個寶牌指示牌。
     *
     * 寶牌指示牌為 1m, 3m，手牌包含 2m x2，胡牌張為 2m，應獲得 3 翻。
     */
    @Test
    fun `test dora calculation with multiple indicators`() {
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 2)
        val doraIndicators = listOf(
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 3)
        )

        val context = createContext(hand, winningTile, isTsumo = true, doraIndicators = doraIndicators)
        val result = calculator.calculate(context)

        val doraResult = result.yakuResults.find { it.yaku == YakuType.Dora }
        assertEquals(4, doraResult?.han, "Should have 4 dora (3x 2m + 1x 4m)")
    }

    /**
     * 測試寶牌計算 - 牌循環（9m 後為 1m）。
     *
     * 寶牌指示牌為 9m，手牌包含 1m（立牌），胡牌張也是 1m，應獲得 2 翻。
     */
    @Test
    fun `test dora calculation with wrap-around`() {
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
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)
        val doraIndicators = listOf(Tile.Numeric(Tile.Suit.Character, 9))

        val context = createContext(hand, winningTile, isTsumo = true, doraIndicators = doraIndicators)
        val result = calculator.calculate(context)

        val doraResult = result.yakuResults.find { it.yaku == YakuType.Dora }
        assertEquals(2, doraResult?.han, "9m indicator should count 1m as dora (1 in hand + 1 as winning)")
    }

    /**
     * 測試寶牌計算 - 字牌循環。
     *
     * 寶牌指示牌為東，手牌包含南 x2（立牌），胡牌張也是南，應獲得 3 翻。
     */
    @Test
    fun `test dora calculation with honor tile wrap-around`() {
        val hand = createHand(
            listOf(
                Tile.Honor.South,
                Tile.Honor.South,
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
        )
        val winningTile = Tile.Honor.South
        val doraIndicators = listOf(Tile.Honor.East)

        val context = createContext(hand, winningTile, isTsumo = true, doraIndicators = doraIndicators)
        val result = calculator.calculate(context)

        val doraResult = result.yakuResults.find { it.yaku == YakuType.Dora }
        assertEquals(3, doraResult?.han, "East indicator should count South as dora (2 in hand + 1 as winning)")
    }

    /**
     * 測試赤寶牌計算。
     *
     * 手牌包含赤 5m，應獲得 1 翻。
     */
    @Test
    fun `test aka dora calculation`() {
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5, isRed = true),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        val akaDoraResult = result.yakuResults.find { it.yaku == YakuType.AkaDora }
        assertEquals(1, akaDoraResult?.han, "Should have 1 aka dora")
    }

    /**
     * 測試立直役種。
     *
     * 宣告立直，應獲得 1 翻。
     */
    @Test
    fun `test riichi yaku`() {
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
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = true, isRiichi = true)
        val result = calculator.calculate(context)

        val riichiResult = result.yakuResults.find { it.yaku == YakuType.Riichi }
        assertEquals(1, riichiResult?.han, "Riichi should be 1 han")
    }

    /**
     * 測試雙立直役種。
     *
     * 宣告雙立直，應獲得 2 翻。
     */
    @Test
    fun `test double riichi yaku`() {
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
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = true, isRiichi = true, isDoubleRiichi = true)
        val result = calculator.calculate(context)

        val riichiResult = result.yakuResults.find { it.yaku == YakuType.Riichi }
        assertEquals(2, riichiResult?.han, "Double Riichi should be 2 han")
    }

    /**
     * 測試一發役種。
     *
     * 應用一發，應獲得 1 翻。
     */
    @Test
    fun `test ippatsu yaku`() {
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
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = true, isRiichi = true, isIppatsu = true)
        val result = calculator.calculate(context)

        val ippatsuResult = result.yakuResults.find { it.yaku == YakuType.Ippatsu }
        assertEquals(1, ippatsuResult?.han, "Ippatsu should be 1 han")
    }

    /**
     * 測試嶺上花役種。
     *
     * 嶺上花，應獲得 1 翻。
     */
    @Test
    fun `test rinshan kaihou yaku`() {
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
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = true, isRinshanKaihou = true)
        val result = calculator.calculate(context)

        val rinshanResult = result.yakuResults.find { it.yaku == YakuType.RinshanKaihou }
        assertEquals(1, rinshanResult?.han, "Rinshan Kaihou should be 1 han")
    }

    /**
     * 測試海底撈月役種。
     *
     * 自摸海底摸牌，應獲得 1 翻。
     */
    @Test
    fun `test haitei yaku`() {
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
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = true, isLastDraw = true)
        val result = calculator.calculate(context)

        val haiteiResult = result.yakuResults.find { it.yaku == YakuType.Haitei }
        assertEquals(1, haiteiResult?.han, "Haitei should be 1 han")
    }

    /**
     * 測試河底撈魚役種。
     *
     * 榮和海底牌，應獲得 1 翻。
     */
    @Test
    fun `test houtei yaku`() {
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
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = false, isLastDiscard = true)
        val result = calculator.calculate(context)

        val houteiResult = result.yakuResults.find { it.yaku == YakuType.Houtei }
        assertEquals(1, houteiResult?.han, "Houtei should be 1 han")
    }

    /**
     * 測試槓槓役種 - 單一明槓。
     *
     * 有一個明槓，應獲得 1 翻。
     */
    @Test
    fun `test chankan with one exposed kan`() {
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
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)
        val revealedExposedKans = listOf(Tile.Numeric(Tile.Suit.Dot, 5))

        val context = createContext(
            hand, winningTile, isTsumo = true,
            revealedExposedKans = revealedExposedKans
        )
        val result = calculator.calculate(context)

        val chankanResult = result.yakuResults.find { it.yaku == YakuType.Chankan }
        assertEquals(1, chankanResult?.han, "One exposed kan should give 1 han")
    }

    /**
     * 測試槓槓役種 - 兩個明槓。
     *
     * 有兩個明槓，應獲得 2 翻。
     */
    @Test
    fun `test chankan with two exposed kans`() {
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
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)
        val revealedExposedKans = listOf(
            Tile.Numeric(Tile.Suit.Dot, 5),
            Tile.Numeric(Tile.Suit.Bamboo, 5)
        )

        val context = createContext(
            hand, winningTile, isTsumo = true,
            revealedExposedKans = revealedExposedKans
        )
        val result = calculator.calculate(context)

        val chankanResult = result.yakuResults.find { it.yaku == YakuType.Chankan }
        assertEquals(2, chankanResult?.han, "Two exposed kans should give 2 han")
    }

    /**
     * 測試搶槓役種。
     *
     * 搶槓，應獲得 1 翻。
     */
    @Test
    fun `test robbing kan yaku`() {
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
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = false, isRobbingKan = true)
        val result = calculator.calculate(context)

        val chankanResult = result.yakuResults.find { it.yaku == YakuType.Chankan }
        assertEquals(1, chankanResult?.han, "Robbing Kan should give 1 han")
    }

    /**
     * 測試役滿計算 - 單一役滿。
     *
     * 有一個役滿，totalHan 應為 -1。
     */
    @Test
    @Ignore("TODO: 實作役滿檢測")
    fun `test yakuman calculation single`() {
        val hand = createHand(
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
                Tile.Honor.Green
            )
        )
        val winningTile = Tile.Honor.Green

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-1, result.totalHan, "Single yakuman should be -1")
    }

    /**
     * 測試役滿計算 - 雙倍役滿。
     *
     * 有一個雙倍役滿，totalHan 應為 -2。
     */
    @Test
    @Ignore("TODO: 實作役滿檢測")
    fun `test double yakuman calculation`() {
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
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
                Tile.Honor.Green
            )
        )
        val winningTile = Tile.Honor.Green

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-2, result.totalHan, "Double yakuman should be -2")
    }

    /**
     * 測試役滿計算 - 兩個一般役滿。
     *
     * 有兩個一般役滿，totalHan 應為 -2。
     */
    @Test
    @Ignore("TODO: 實作役滿檢測")
    fun `test two yakuman calculation`() {
        val hand = createHand(
            listOf(
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
                Tile.Honor.Green
            )
        )
        val winningTile = Tile.Honor.Green

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-2, result.totalHan, "Two yakuman should be -2")
    }

    /**
     * 測試役滿計算 - 雙倍役滿加一般役滿。
     *
     * 有一個雙倍役滿和一個一般役滿，totalHan 應為 -3。
     */
    @Test
    @Ignore("TODO: 實作役滿檢測")
    fun `test double yakuman plus yakuman calculation`() {
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
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
                Tile.Honor.Green
            )
        )
        val winningTile = Tile.Honor.Green

        val context = createContext(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-3, result.totalHan, "Double yakuman + yakuman should be -3")
    }
}
