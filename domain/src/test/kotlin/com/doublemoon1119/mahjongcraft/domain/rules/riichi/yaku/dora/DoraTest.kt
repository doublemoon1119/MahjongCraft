package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.dora

import com.doublemoon1119.mahjongcraft.domain.base.Meld
import com.doublemoon1119.mahjongcraft.domain.base.MeldType
import com.doublemoon1119.mahjongcraft.domain.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.testing.domain.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.domain.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.domain.rules.riichi.FakeRiichiHandValueContextFactory
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 立直麻將手牌番數計算機之寶牌測試。
 *
 * @see com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandValueCalculator
 */
class DoraTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試寶牌計算 - 單一寶牌指示牌。
     *
     * 寶牌指示牌為 5m，手牌包含 6m（立牌），胡牌張也是 6m，應獲得 2 翻。
     */
    @Test
    fun `test dora calculation with single indicator`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 6)
        val doraIndicators = listOf(Tile.Numeric(Tile.Suit.Character, 5))

        val context =
            FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, doraIndicators = doraIndicators)
        val result = calculator.calculate(context)

        val doraResult = result.yakuResults.find { it.yaku == YakuType.Dora }
        assertEquals(2, doraResult?.han, "Should have 2 dora (one in hand + one as winning tile)")
    }

    /**
     * 測試寶牌計算 - 多個寶牌指示牌。
     *
     * 寶牌指示牌為 1m, 3m，手牌包含 2m x2，胡牌張為 2m，應獲得 4 翻。
     */
    @Test
    fun `test dora calculation with multiple indicators`() {
        val hand = FakeHandFactory.create(
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

        val context =
            FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, doraIndicators = doraIndicators)
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
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)
        val doraIndicators = listOf(Tile.Numeric(Tile.Suit.Character, 9))

        val context =
            FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, doraIndicators = doraIndicators)
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
        val hand = FakeHandFactory.create(
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
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Honor.South
        val doraIndicators = listOf(Tile.Honor.East)

        val context =
            FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, doraIndicators = doraIndicators)
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
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5, isRed = true),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        val akaDoraResult = result.yakuResults.find { it.yaku == YakuType.AkaDora }
        assertEquals(1, akaDoraResult?.han, "Should have 1 aka dora")
    }

    /**
     * 測試赤寶牌計算 - 胡牌張為赤寶牌。
     *
     * 胡牌張為赤 5m，應獲得 1 翻。
     */
    @Test
    fun `test aka dora calculation with winning tile`() {
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
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Dot, 5, isRed = true)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        val akaDoraResult = result.yakuResults.find { it.yaku == YakuType.AkaDora }
        assertEquals(1, akaDoraResult?.han, "Should have 1 aka dora from winning tile")
    }

    /**
     * 測試裏寶牌計算 - 立直時。
     *
     * 裏寶牌指示牌為 5m，手牌包含 6m（立牌），胡牌張也是 6m，應獲得 2 翻。
     */
    @Test
    fun `test ura dora calculation with riichi`() {
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
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 6)
        val doraIndicators = listOf(Tile.Numeric(Tile.Suit.Character, 5))
        val uraDoraIndicators = listOf(Tile.Numeric(Tile.Suit.Character, 5))

        val context = FakeRiichiHandValueContextFactory.create(
            hand = hand,
            winningTile = winningTile,
            isTsumo = true,
            isRiichi = true,
            doraIndicators = doraIndicators,
            uraDoraIndicators = uraDoraIndicators
        )
        val result = calculator.calculate(context)

        val uraDoraResult = result.yakuResults.find { it.yaku == YakuType.UraDora }
        assertEquals(2, uraDoraResult?.han, "Should have 2 ura dora (one in hand + one as winning tile)")
    }

    /**
     * 測試裏寶牌計算 - 非立直時不計算。
     *
     * 非立直時，裏寶牌不應被計算。
     */
    @Test
    fun `test ura dora calculation without riichi returns null`() {
        val hand = FakeHandFactory.create(
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
        val uraDoraIndicators = listOf(Tile.Numeric(Tile.Suit.Character, 5))

        val context = FakeRiichiHandValueContextFactory.create(
            hand = hand,
            winningTile = winningTile,
            isTsumo = true,
            isRiichi = false,
            uraDoraIndicators = uraDoraIndicators
        )
        val result = calculator.calculate(context)

        val uraDoraResult = result.yakuResults.find { it.yaku == YakuType.UraDora }
        assertEquals(null, uraDoraResult, "Should not have ura dora without riichi")
    }

    /**
     * 測試赤寶牌計算 - 副露中的赤寶牌。
     *
     * 副露包含赤 5p x3，應獲得 3 翻。
     */
    @Test
    fun `test aka dora calculation with exposed meld`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1)
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5, isRed = true)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5, isRed = true)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5, isRed = true))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 5)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        val akaDoraResult = result.yakuResults.find { it.yaku == YakuType.AkaDora }
        assertEquals(3, akaDoraResult?.han, "Should have 3 aka dora from exposed meld (3 tiles)")
    }

    /**
     * 測試赤寶牌計算 - 多張赤寶牌。
     *
     * 手牌包含赤 5p x2，副露包含赤 5m x3，胡牌張為赤 5s，應獲得 6 翻。
     */
    @Test
    fun `test aka dora calculation with multiple`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Dot, 5, isRed = true),
                Tile.Numeric(Tile.Suit.Dot, 5, isRed = true),
                Tile.Numeric(Tile.Suit.Dot, 7),
                Tile.Numeric(Tile.Suit.Dot, 8),
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 4)
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5, isRed = true)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5, isRed = true)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5, isRed = true))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 5, isRed = true)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        val akaDoraResult = result.yakuResults.find { it.yaku == YakuType.AkaDora }
        assertEquals(6, akaDoraResult?.han, "Should have 6 aka dora (2 in hand + 3 in meld + 1 as winning)")
    }
}
