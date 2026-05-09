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
 * 純全帶么九 (Junchan) 測試。
 */
class JunchanTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 純全帶么九 (Junchan) 測試。
     */
    @Test
    fun `test junchan menzen`() {
        // 手牌：111m (刻子), 111p (刻子), 111s (刻子), 789m (順子), 9s, 9s (餘牌)
        // 所有面子和雀頭都包含老頭牌，無字牌
        // 門前清：3 翻
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 9)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 9)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val junchanResult = result.yakuResults.find { it.yaku == YakuType.Junchan }
        assertNotNull(junchanResult, "Should have Junchan")
        assertEquals(3, junchanResult.han, "Menzen Junchan should be 3 han")
    }

    @Test
    fun `test junchan with fuuro`() {
        // 手牌：111m (刻子), 999p (刻子), 111s (刻子), 789m (順子), 9s, 9s (餘牌)
        // 所有面子和雀頭都包含老頭牌，無字牌
        // 副露：2 翻
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 9)
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 9)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = false, isMenzen = false)
        val result = calculator.calculate(context)

        val junchanResult = result.yakuResults.find { it.yaku == YakuType.Junchan }
        assertNotNull(junchanResult, "Should have Junchan")
        assertEquals(2, junchanResult.han, "Open Junchan should be 2 han")
    }

    @Test
    fun `test junchan with honor tiles returns null`() {
        // 手牌：含有字牌，應該是 Honchan 不是 Junchan
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Honor.Red,
                Tile.Honor.Red
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Honor.Red

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val junchanResult = result.yakuResults.find { it.yaku == YakuType.Junchan }
        assertNull(junchanResult, "Should not have Junchan with honor tiles")
    }
}