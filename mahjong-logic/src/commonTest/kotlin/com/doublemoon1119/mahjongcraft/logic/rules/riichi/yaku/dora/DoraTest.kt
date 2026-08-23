package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.dora
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.FakeRiichiHandValueContextFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 立直麻將手牌番數計算機之寶牌測試。
 *
 * @see com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueCalculator
 */
class DoraTest : RiichiHandValueCalculatorTestBase() {

    /** 驗證三種赤五指示牌都會先視為普通五，再將同花色的六判定為寶牌。 */
    @Test
    fun `test red five indicators resolve to six of the same suit`() {
        Tile.Suit.entries.forEach { suit ->
            assertEquals(
                Tile.Numeric(suit, 6),
                getNextDora(RiichiTileTypes.redFive(suit)),
                "Red five indicator should resolve to six of the same suit",
            )
        }
    }

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
                Tile.Numeric(Tile.Suit.Dot, 1),
            ),
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 6)
        val doraIndicators = listOf(Tile.Numeric(Tile.Suit.Character, 5))

        val context =
            FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, doraIndicators = doraIndicators)
        val result = calculator.calculate(context)

        val doraResult = result.yakuResults.find { it.yaku == YakuType.Dora }
        assertEquals(2, doraResult?.han, "Should have 2 dora (one in hand + one as winning tile)")
    }

    /** 驗證赤五指示牌在實際番數計算中，會把同花色的六計為寶牌。 */
    @Test
    fun `test dora calculation with red five indicator`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 7),
                Tile.Numeric(Tile.Suit.Bamboo, 8),
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
            ),
        )
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 6)
        val doraIndicators = listOf(RiichiTileTypes.redFive(Tile.Suit.Bamboo))

        val context =
            FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, doraIndicators = doraIndicators)
        val result = calculator.calculate(context)

        val doraResult = result.yakuResults.find { it.yaku == YakuType.Dora }
        assertEquals(2, doraResult?.han, "Red five bamboo indicator should count six bamboo as dora")
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
                Tile.Numeric(Tile.Suit.Dot, 1),
            ),
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 2)
        val doraIndicators = listOf(
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 3),
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
                Tile.Numeric(Tile.Suit.Dot, 1),
            ),
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
                Tile.Numeric(Tile.Suit.Dot, 1),
            ),
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
                RiichiTileTypes.redFive(Tile.Suit.Character),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
            ),
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
                Tile.Numeric(Tile.Suit.Dot, 4),
            ),
        )
        val winningTile = RiichiTileTypes.redFive(Tile.Suit.Dot)

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
                Tile.Numeric(Tile.Suit.Dot, 1),
            ),
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
            uraDoraIndicators = uraDoraIndicators,
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
                Tile.Numeric(Tile.Suit.Dot, 1),
            ),
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 6)
        val uraDoraIndicators = listOf(Tile.Numeric(Tile.Suit.Character, 5))

        val context = FakeRiichiHandValueContextFactory.create(
            hand = hand,
            winningTile = winningTile,
            isTsumo = true,
            isRiichi = false,
            uraDoraIndicators = uraDoraIndicators,
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
                Tile.Numeric(Tile.Suit.Dot, 1),
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(RiichiTileTypes.redFive(Tile.Suit.Dot)),
                        FakeIdentifiedTileFactory.create(RiichiTileTypes.redFive(Tile.Suit.Dot)),
                        FakeIdentifiedTileFactory.create(RiichiTileTypes.redFive(Tile.Suit.Dot)),
                    ),
                    sourceDirection = RelativeDirection.Left,
                ),
            ),
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
                RiichiTileTypes.redFive(Tile.Suit.Dot),
                RiichiTileTypes.redFive(Tile.Suit.Dot),
                Tile.Numeric(Tile.Suit.Dot, 7),
                Tile.Numeric(Tile.Suit.Dot, 8),
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(RiichiTileTypes.redFive(Tile.Suit.Character)),
                        FakeIdentifiedTileFactory.create(RiichiTileTypes.redFive(Tile.Suit.Character)),
                        FakeIdentifiedTileFactory.create(RiichiTileTypes.redFive(Tile.Suit.Character)),
                    ),
                    sourceDirection = RelativeDirection.Left,
                ),
            ),
        )
        val winningTile = RiichiTileTypes.redFive(Tile.Suit.Bamboo)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        val akaDoraResult = result.yakuResults.find { it.yaku == YakuType.AkaDora }
        assertEquals(6, akaDoraResult?.han, "Should have 6 aka dora (2 in hand + 3 in meld + 1 as winning)")
    }
}
