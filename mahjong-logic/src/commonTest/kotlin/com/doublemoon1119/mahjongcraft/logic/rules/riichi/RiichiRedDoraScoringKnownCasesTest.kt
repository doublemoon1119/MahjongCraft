package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileValues
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeMeldFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.FakeRiichiHandValueContextFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 赤寶牌計分的已知案例：赤五在手牌、和牌張與副露中各算一翻，但不算起和役。
 *
 * 赤五只透過 [redFive] 與 [redFiveValue] 建立，案例與預期點數不依賴赤五的表示方式。
 */
class RiichiRedDoraScoringKnownCasesTest : RiichiHandValueCalculatorTestBase() {
    /** 門前手牌中的一張赤五加一翻：斷么九、赤寶牌，單騎榮和 2 翻 40 符。 */
    @Test
    fun `red five in a closed hand adds one han`() {
        val result = calculator.calculate(
            ron(
                hand = Hand(tiles = man(2, 3, 4) + listOf(redFive(Tile.Suit.Dot)) + pin(6, 7) + sou(3, 4, 5, 6, 7, 8) + pin(8)),
                winningTile = TileValue(Tile.Numeric(Tile.Suit.Dot, 8)),
            ),
        )

        assertEquals(mapOf(YakuType.Tanyao to 1, YakuType.AkaDora to 1), result.hanByYaku())
        assertEquals(40, result.totalFu)
        assertEquals(RiichiPointResult.Ron(2600), result.pointResult)
    }

    /** 同一手牌改成普通五時少一翻，證明加算只來自赤寶牌特性。 */
    @Test
    fun `plain five in the same hand adds nothing`() {
        val result = calculator.calculate(
            ron(
                hand = Hand(tiles = man(2, 3, 4) + pin(5, 6, 7) + sou(3, 4, 5, 6, 7, 8) + pin(8)),
                winningTile = TileValue(Tile.Numeric(Tile.Suit.Dot, 8)),
            ),
        )

        assertEquals(mapOf(YakuType.Tanyao to 1), result.hanByYaku())
        assertEquals(RiichiPointResult.Ron(1300), result.pointResult)
    }

    /** 自摸到的赤五也算：門前清自摸、平和、斷么九、赤寶牌，4 翻 20 符。 */
    @Test
    fun `red five as the winning tile counts`() {
        val context = FakeRiichiHandValueContextFactory.create(
            hand = Hand(tiles = man(2, 3, 4) + pin(6, 7) + sou(3, 4, 5, 6, 7, 8) + pin(8, 8)),
            winningTile = Tile.Numeric(Tile.Suit.Dot, 8),
            isTsumo = true,
            seatWind = Wind.SOUTH,
        ).copy(winningTileValue = redFiveValue(Tile.Suit.Dot))

        val result = calculator.calculate(context)

        assertEquals(
            mapOf(YakuType.Menzentsumo to 1, YakuType.Pinfu to 1, YakuType.Tanyao to 1, YakuType.AkaDora to 1),
            result.hanByYaku(),
        )
        assertEquals(20, result.totalFu)
        assertEquals(RiichiPointResult.NonDealerTsumo(dealerPayment = 2600, otherNonDealerPayment = 1300), result.pointResult)
    }

    /** 副露中的赤五與手牌中的赤五都算：食斷加兩張赤寶牌，3 翻 30 符。 */
    @Test
    fun `red fives in melds and in the hand all count`() {
        val calledFive = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5))
        val pon = FakeMeldFactory.create(
            type = MeldType.PON,
            tiles = listOf(redFive(Tile.Suit.Dot), FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5)), calledFive),
            sourceTile = calledFive,
            sourceDirection = RelativeDirection.Left,
        )
        val result = calculator.calculate(
            ron(
                hand = Hand(tiles = man(2, 3, 4, 6, 7, 8) + sou(3, 4) + listOf(redFive(Tile.Suit.Bamboo)) + sou(6), melds = listOf(pon)),
                winningTile = TileValue(Tile.Numeric(Tile.Suit.Bamboo, 6)),
                isMenzen = false,
            ),
        )

        assertEquals(mapOf(YakuType.Tanyao to 1, YakuType.AkaDora to 2), result.hanByYaku())
        assertEquals(30, result.totalFu)
        assertEquals(RiichiPointResult.Ron(3900), result.pointResult)
    }

    /** 只有赤寶牌時仍沒有起和役：番數照算，但起和門檻不成立。 */
    @Test
    fun `red dora alone does not satisfy the yaku requirement`() {
        val ones = List(3) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)) }
        val pon = FakeMeldFactory.create(type = MeldType.PON, tiles = ones, sourceTile = ones.last(), sourceDirection = RelativeDirection.Across)
        val result = calculator.calculate(
            ron(
                hand = Hand(tiles = man(2, 3, 4) + listOf(redFive(Tile.Suit.Dot)) + pin(6, 7) + sou(3, 4, 5) + pin(8), melds = listOf(pon)),
                winningTile = TileValue(Tile.Numeric(Tile.Suit.Dot, 8)),
                isMenzen = false,
            ),
        )

        assertEquals(mapOf(YakuType.AkaDora to 1), result.hanByYaku())
        assertEquals(0, result.qualifyingHan().value)
        assertFalse(result.qualifyingHan().satisfies(1))
    }

    /**
     * 建立非莊家榮和的計分情境。
     *
     * @param hand 不含和牌張的手牌。
     * @param winningTile 和牌張的完整牌值。
     * @param isMenzen 是否門前清。
     * @return 南家榮和的計分情境。
     */
    private fun ron(
        hand: Hand,
        winningTile: TileValue,
        isMenzen: Boolean = true,
    ): RiichiHandValueContext = FakeRiichiHandValueContextFactory.create(
        hand = hand,
        winningTile = winningTile.tile,
        isTsumo = false,
        isMenzen = isMenzen,
        seatWind = Wind.SOUTH,
    ).copy(winningTileValue = winningTile)

    /** 依役種彙整番數，只保留有番數的項目。 */
    private fun RiichiHandValueResult.hanByYaku(): Map<YakuType, Int> = yakuResults.filter { it.han != 0 }.associate { it.yaku to it.han }

    /** 建立指定花色的赤五牌張。 */
    private fun redFive(suit: Tile.Suit): IdentifiedTile = FakeIdentifiedTileFactory.create(RiichiTileValues.redFive(suit))

    /** 建立指定花色赤五的牌值。 */
    private fun redFiveValue(suit: Tile.Suit): TileValue = RiichiTileValues.redFive(suit)

    /** 建立萬子牌張。 */
    private fun man(vararg values: Int): List<IdentifiedTile> = numeric(Tile.Suit.Character, values)

    /** 建立筒子牌張。 */
    private fun pin(vararg values: Int): List<IdentifiedTile> = numeric(Tile.Suit.Dot, values)

    /** 建立條子牌張。 */
    private fun sou(vararg values: Int): List<IdentifiedTile> = numeric(Tile.Suit.Bamboo, values)

    /** 依花色與數字建立普通數牌牌張。 */
    private fun numeric(suit: Tile.Suit, values: IntArray): List<IdentifiedTile> = values.map { FakeIdentifiedTileFactory.create(Tile.Numeric(suit, it)) }
}
