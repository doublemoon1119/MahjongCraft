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
 * 驗證莊家判定不會跟著場風（圈風）誤判：莊家的自風永遠是東，即使場風已經推進到南場，
 * 只要自風仍是東就應該正確判定為莊家；反之，自風恰好等於場風（例如南場時自風為南）的
 * 玩家不應被誤判為莊家。
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
     * 驗證南場時，自風仍為東的玩家（即莊家）自摸應套用莊家點數倍率，
     * 不會因為場風（南）跟自風（東）不同就被誤判為非莊家。
     */
    @Test
    fun `test dealer with seat wind east still gets dealer multiplier during south round`() {
        val context = FakeRiichiHandValueContextFactory.create(
            hand = daisangenHand(),
            winningTile = Tile.Honor.Red,
            isTsumo = true,
            roundWind = Wind.SOUTH,
            seatWind = Wind.EAST,
        )

        val result = calculator.calculate(context)

        assertEquals(RiichiPointResult.DealerTsumo(paymentPerNonDealer = 16000), result.pointResult)
    }

    /**
     * 驗證南場時，自風恰好也是南（等於場風）的玩家自摸應套用非莊家點數倍率，
     * 不會因為自風跟場風剛好相同就被誤判為莊家（這正是連風牌／雙東成立的條件，
     * 跟「是否為莊家」是兩件不同的事）。
     */
    @Test
    fun `test player whose seat wind matches round wind is not misidentified as dealer`() {
        val context = FakeRiichiHandValueContextFactory.create(
            hand = daisangenHand(),
            winningTile = Tile.Honor.Red,
            isTsumo = true,
            roundWind = Wind.SOUTH,
            seatWind = Wind.SOUTH,
        )

        val result = calculator.calculate(context)

        assertEquals(
            RiichiPointResult.NonDealerTsumo(dealerPayment = 16000, otherNonDealerPayment = 8000),
            result.pointResult,
        )
    }
}
