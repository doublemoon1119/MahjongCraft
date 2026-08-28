package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.FakeRiichiHandValueContextFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [RiichiHandValueCalculator] 對莊家判定的整合測試。
 *
 * 驗證莊家點數只讀取權威 `isDealer`，不會從場風或規則可變的自風反推。
 *
 * @see PointCalculator
 */
class RiichiHandValueCalculatorDealerTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 手牌：中中、發發發、白白白、123m、55p
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
            Tile.Numeric(Tile.Suit.Dot, 5),
        ),
    )

    /**
     * 驗證開門定風讓莊家取得南風時，仍套用莊家點數倍率。
     */
    @Test
    fun `test authoritative dealer with south seat wind gets dealer multiplier`() {
        val context = FakeRiichiHandValueContextFactory.create(
            hand = daisangenHand(),
            winningTile = Tile.Honor.Red,
            isTsumo = true,
            roundWind = Wind.SOUTH,
            seatWind = Wind.SOUTH,
            isDealer = true,
        )

        val result = calculator.calculate(context)

        assertEquals(RiichiPointResult.DealerTsumo(paymentPerNonDealer = 16000), result.pointResult)
    }

    /**
     * 驗證非莊家即使取得東風，也不會被誤判為莊家。
     */
    @Test
    fun `test non dealer with east seat wind keeps non dealer multiplier`() {
        val context = FakeRiichiHandValueContextFactory.create(
            hand = daisangenHand(),
            winningTile = Tile.Honor.Red,
            isTsumo = true,
            roundWind = Wind.SOUTH,
            seatWind = Wind.EAST,
            isDealer = false,
        )

        val result = calculator.calculate(context)

        assertEquals(
            RiichiPointResult.NonDealerTsumo(dealerPayment = 16000, otherNonDealerPayment = 8000),
            result.pointResult,
        )
    }
}
