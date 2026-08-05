package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.FakeRiichiHandValueContextFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [RiichiHandValueCalculator] 對包牌（Pao）情境的整合測試。
 *
 * 驗證 [RiichiHandValueContext.paoLiability] 如何影響最終的 [RiichiPointResult]，
 * 以及 [RiichiHandValueResult.paoLiability] 的透傳行為。
 *
 * @see PaoDetector
 * @see PointCalculator
 */
class RiichiHandValueCalculatorPaoTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 手牌：中中中、發發發、白白白、123m、55p
     * 胡牌：中（自摸）
     */
    private fun daisangenHand() = FakeHandFactory.create(
        listOf(
            Tile.Honor.Red, Tile.Honor.Red,
            Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
            Tile.Honor.White, Tile.Honor.White, Tile.Honor.White,
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 2),
            Tile.Numeric(Tile.Suit.Character, 3),
            Tile.Numeric(Tile.Suit.Dot, 5),
            Tile.Numeric(Tile.Suit.Dot, 5)
        )
    )

    /**
     * 測試大三元自摸胡牌，且包牌責任已成立時，點數結算應改為 [RiichiPointResult.PaoTsumo]，
     * 並將 [PaoLiability] 透傳至結果中。
     */
    @Test
    fun `test daisangen tsumo with pao liability settles as PaoTsumo`() {
        val context = FakeRiichiHandValueContextFactory.create(
            hand = daisangenHand(),
            winningTile = Tile.Honor.Red,
            isTsumo = true,
            seatWind = Wind.SOUTH,
            paoLiability = PaoLiability(PaoYaku.Daisangen, RelativeDirection.Left)
        )

        val result = calculator.calculate(context)

        assertTrue(result.yakuResults.any { it.yaku == YakuType.Daisangen })
        assertEquals(PaoLiability(PaoYaku.Daisangen, RelativeDirection.Left), result.paoLiability)
        assertEquals(RiichiPointResult.PaoTsumo(32000), result.pointResult)
    }

    /**
     * 測試大三元榮和胡牌，且包牌責任已成立時，點數結算應改為 [RiichiPointResult.PaoRon]。
     */
    @Test
    fun `test daisangen ron with pao liability settles as PaoRon`() {
        val context = FakeRiichiHandValueContextFactory.create(
            hand = daisangenHand(),
            winningTile = Tile.Honor.Red,
            isTsumo = false,
            seatWind = Wind.SOUTH,
            paoLiability = PaoLiability(PaoYaku.Daisangen, RelativeDirection.Across)
        )

        val result = calculator.calculate(context)

        assertTrue(result.yakuResults.any { it.yaku == YakuType.Daisangen })
        assertEquals(PaoLiability(PaoYaku.Daisangen, RelativeDirection.Across), result.paoLiability)
        assertEquals(RiichiPointResult.PaoRon(16000), result.pointResult)
    }

    /**
     * 測試包牌責任已成立，但成立的種類（大四喜）與實際胡出的役滿（大三元）不符時，
     * 不應套用包牌，須以一般自摸/榮和的方式結算，且結果中的包牌責任應為 null。
     *
     * 這是防止「狀態成立後手牌卻演變成其他役滿」誤用包牌的防呆檢查。
     */
    @Test
    fun `test mismatched pao yaku does not apply pao settlement`() {
        val context = FakeRiichiHandValueContextFactory.create(
            hand = daisangenHand(),
            winningTile = Tile.Honor.Red,
            isTsumo = true,
            seatWind = Wind.SOUTH,
            paoLiability = PaoLiability(PaoYaku.Daisuushii, RelativeDirection.Left)
        )

        val result = calculator.calculate(context)

        assertTrue(result.yakuResults.any { it.yaku == YakuType.Daisangen })
        assertNull(result.paoLiability, "Pao should not apply when armed yaku does not match the actual win")
        assertEquals(RiichiPointResult.NonDealerTsumo(dealerPayment = 16000, otherNonDealerPayment = 8000), result.pointResult)
    }

    /**
     * 測試沒有包牌責任時，維持一般自摸的結算方式。
     */
    @Test
    fun `test daisangen tsumo without pao liability settles normally`() {
        val context = FakeRiichiHandValueContextFactory.create(
            hand = daisangenHand(),
            winningTile = Tile.Honor.Red,
            isTsumo = true,
            seatWind = Wind.SOUTH
        )

        val result = calculator.calculate(context)

        assertTrue(result.yakuResults.any { it.yaku == YakuType.Daisangen })
        assertNull(result.paoLiability)
        assertEquals(RiichiPointResult.NonDealerTsumo(dealerPayment = 16000, otherNonDealerPayment = 8000), result.pointResult)
    }
}
