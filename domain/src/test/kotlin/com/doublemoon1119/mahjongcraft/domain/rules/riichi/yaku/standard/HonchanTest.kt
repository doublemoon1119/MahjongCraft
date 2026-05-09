package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 混全帶么九 (Honchan) 測試。
 */
class HonchanTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 混全帶么九 (Honchan) 測試。
     */
    @Test
    fun `test honchan menzen`() {
        // 手牌：123m (順子), 789p (刻子), 111s (刻子), 111z (刻子), 6z (聽發)
        // 所有面子和雀頭都包含么九牌
        // 門前清：2 翻
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Dot, 7),
                Tile.Numeric(Tile.Suit.Dot, 8),
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.Green
            )
        )
        val winningTile = Tile.Honor.Green

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val honchanResult = result.yakuResults.find { it.yaku == YakuType.Honchan }
        assertNotNull(honchanResult, "Should have Honchan")
        assertEquals(2, honchanResult.han, "Menzen Honchan should be 2 han")
    }

    @Test
    fun `test honchan with fuuro`() {
        // 手牌：123m (順子), 789p (刻子), 111s (刻子), 111z (刻子), 66z (雀頭)
        // 所有面子和雀頭都包含么九牌
        // 門前清：2 翻
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Dot, 7),
                Tile.Numeric(Tile.Suit.Dot, 8),
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Honor.Green
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Honor.East),
                        FakeIdentifiedTileFactory.create(Tile.Honor.East),
                        FakeIdentifiedTileFactory.create(Tile.Honor.East)
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Honor.Green

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = false, isMenzen = false)
        val result = calculator.calculate(context)

        val honchanResult = result.yakuResults.find { it.yaku == YakuType.Honchan }
        assertNotNull(honchanResult, "Should have Honchan")
        assertEquals(1, honchanResult.han, "Open Honchan should be 1 han")
    }

    @Test
    fun `test honchan with non-terminal mentsu returns null`() {
        // 手牌：123m, 789p, 111z, 456s, 77z (雀頭)
        // 456s 不包含么九牌，不構成混全帶么九
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Dot, 7),
                Tile.Numeric(Tile.Suit.Dot, 8),
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Honor.East,
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Honor.Red
            )
        )
        val winningTile = Tile.Honor.Red

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val honchanResult = result.yakuResults.find { it.yaku == YakuType.Honchan }
        assertNull(honchanResult, "Should not have Honchan with non-terminal mentsu")
    }
}
