package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.domain.base.Meld
import com.doublemoon1119.mahjongcraft.domain.base.MeldType
import com.doublemoon1119.mahjongcraft.domain.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.testing.domain.fakes.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.domain.fakes.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.domain.fakes.rules.riichi.FakeRiichiHandValueContextFactory
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 清一色與混一色役種測試。
 *
 * 測試清一色與混一色之番數計算，以及兩者互斥時之正確行為。
 *
 * @see com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandValueCalculator
 */
class ChinitsuTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試混一色 - 門前清。
     *
     * 手牌僅有一種數牌花色 + 字牌，應獲得 3 翻。
     */
    @Test
    fun `test honitsu`() {
        val hand = FakeHandFactory.create(
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
                Tile.Honor.South
            )
        )
        val winningTile = Tile.Honor.South

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
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
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 7)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val honitsuResult = result.yakuResults.find { it.yaku == YakuType.Honitsu }
        assertEquals(2, honitsuResult?.han, "Honitsu should be 2 han with fuuro")
    }

    /**
     * 測試清一色 - 門前清。
     *
     * 手牌僅有一種數牌花色（無字牌），應獲得 6 翻。
     */
    @Test
    fun `test chinitsu`() {
        val hand = FakeHandFactory.create(
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
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 2)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 2)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
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
        val hand = FakeHandFactory.create(
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
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 9)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = false)
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
        val hand = FakeHandFactory.create(
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
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 2)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 2)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        val chinitsuResult = result.yakuResults.find { it.yaku == YakuType.Chinitsu }
        val honitsuResult = result.yakuResults.find { it.yaku == YakuType.Honitsu }

        assertEquals(6, chinitsuResult?.han, "Chinitsu should be 6 han")
        assertNull(honitsuResult, "Honitsu should not be present when Chinitsu is present")
    }
}
