package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.special

import com.doublemoon1119.mahjongcraft.domain.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.domain.base.Meld
import com.doublemoon1119.mahjongcraft.domain.base.MeldType
import com.doublemoon1119.mahjongcraft.domain.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 立直麻將手牌番數計算機之特殊役種測試。
 *
 * 測試內容涵蓋立直、一發、嶺上花、海底撈月、河底撈魚、槓槓、搶槓等役種。
 *
 * @see com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandValueCalculator
 */
class SpecialYakuTest : RiichiHandValueCalculatorTestBase() {

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
     * 測試立直役種。
     *
     * 宣告立直，應獲得 1 翻。
     */
    @Test
    fun `test riichi yaku`() {
        val hand = createHand(createBasicHand())
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
        val hand = createHand(createBasicHand())
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
        val hand = createHand(createBasicHand())
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
        val hand = createHand(createBasicHand())
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
        val hand = createHand(createBasicHand())
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
        val hand = createHand(createBasicHand())
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = false, isLastDiscard = true)
        val result = calculator.calculate(context)

        val houteiResult = result.yakuResults.find { it.yaku == YakuType.Houtei }
        assertEquals(1, houteiResult?.han, "Houtei should be 1 han")
    }

    /**
     * 測試搶槓役種。
     *
     * 搶槓，應獲得 1 翻。
     */
    @Test
    fun `test robbing kan yaku`() {
        val hand = createHand(createBasicHand())
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = false, isRobbingKan = true)
        val result = calculator.calculate(context)

        val chankanResult = result.yakuResults.find { it.yaku == YakuType.Chankan }
        assertEquals(1, chankanResult?.han, "Robbing Kan should give 1 han")
    }

    /**
     * 測試門前清自摸役種。
     *
     * 門前清自摸，應獲得 1 翻。
     */
    @Test
    fun `test menzentsumo yaku`() {
        val hand = createHand(createBasicHand())
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val menzentsumoResult = result.yakuResults.find { it.yaku == YakuType.Menzentsumo }
        assertEquals(1, menzentsumoResult?.han, "Menzentsumo should be 1 han")
    }

    /**
     * 測試門前清自摸役種 - 非自摸不成立。
     *
     * 榮和時不應獲得門前清自摸。
     */
    @Test
    fun `test menzentsumo not valid for ron`() {
        val hand = createHand(createBasicHand())
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = createContext(hand, winningTile, isTsumo = false, isMenzen = true)
        val result = calculator.calculate(context)

        val menzentsumoResult = result.yakuResults.find { it.yaku == YakuType.Menzentsumo }
        assertEquals(null, menzentsumoResult, "Menzentsumo should not be valid for Ron")
    }

    /**
     * 測試門前清自摸役種 - 副露不成立。
     *
     * 副露時不應獲得門前清自摸。
     */
    @Test
    fun `test menzentsumo not valid with meld`() {
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

        val menzentsumoResult = result.yakuResults.find { it.yaku == YakuType.Menzentsumo }
        assertEquals(null, menzentsumoResult, "Menzentsumo should not be valid with meld")
    }
}
