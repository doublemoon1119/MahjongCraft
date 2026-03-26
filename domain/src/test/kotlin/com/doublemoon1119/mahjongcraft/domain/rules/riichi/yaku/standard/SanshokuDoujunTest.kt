package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 三色同順 (Sanshoku Doujun) 測試。
 */
class SanshokuDoujunTest : RiichiHandValueCalculatorTestBase() {

    @Test
    fun `test sanshoku doujun menzen`() {
        // 手牌：123m (順子), 123p (順子), 123s (順子), 44m (雀頭), 57m (餘牌)
        // 門前清：2 翻
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 7)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 6)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val sanshokuDoujunResult = result.yakuResults.find { it.yaku == YakuType.SanshokuDoujun }
        assertNotNull(sanshokuDoujunResult, "Should have SanshokuDoujun")
        assertEquals(2, sanshokuDoujunResult.han, "Menzen SanshokuDoujun should be 2 han")
    }

    @Test
    fun `test sanshoku doujun with fuuro`() {
        // 副露：吃 123m
        // 手牌：123p (順子), 123s (順子), 44m (雀頭), 56m (餘牌)
        // 副露：1 翻
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6)
            ),
            melds = listOf(
                Meld(
                    type = MeldType.CHI,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 1)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 2)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 3))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 7)

        val context = createContext(hand, winningTile, isTsumo = false, isMenzen = false)
        val result = calculator.calculate(context)

        val sanshokuDoujunResult = result.yakuResults.find { it.yaku == YakuType.SanshokuDoujun }
        assertNotNull(sanshokuDoujunResult, "Should have SanshokuDoujun")
        assertEquals(1, sanshokuDoujunResult.han, "Open SanshokuDoujun should be 1 han")
    }

    @Test
    fun `test sanshoku doujun with only two shuntsu returns null`() {
        // 手牌：123m (順子), 123p (順子), 44s (雀頭), 3456789s (餘牌)
        // 只有 2 組順子，不構成三色同順
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 7),
                Tile.Numeric(Tile.Suit.Bamboo, 8),
                Tile.Numeric(Tile.Suit.Bamboo, 9)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 6)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val sanshokuDoujunResult = result.yakuResults.find { it.yaku == YakuType.SanshokuDoujun }
        assertNull(sanshokuDoujunResult, "Should not have SanshokuDoujun with only 2 shuntsu")
    }
}
