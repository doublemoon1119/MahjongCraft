package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.domain.base.Meld
import com.doublemoon1119.mahjongcraft.domain.base.MeldType
import com.doublemoon1119.mahjongcraft.domain.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.fakes.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 混老頭 (Honroutou) 役種測試。
 */
class HonroutouTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 混老頭 (Honroutou) 測試。
     */
    @Test
    fun `test honroutou valid`() {
        // 副露：777z, 999p, 111s,
        // 手牌：11m, 99s (老頭牌)
        // 混老頭：2 翻
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 9)
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Honor.Red),
                        FakeIdentifiedTileFactory.create(Tile.Honor.Red),
                        FakeIdentifiedTileFactory.create(Tile.Honor.Red)
                    ),
                    sourceDirection = RelativeDirection.Left
                ),
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9))
                    ),
                    sourceDirection = RelativeDirection.Left
                ),
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 9))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 9)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val honroutouResult = result.yakuResults.find { it.yaku == YakuType.Honroutou }
        assertNotNull(honroutouResult, "Should have Honroutou")
        assertEquals(2, honroutouResult.han, "Honroutou should be 2 han")
    }

    @Test
    fun `test honroutou with non-routou tile returns null`() {
        // 副露：777z, 999p, 111s,
        // 手牌：22m, 99s (老頭牌)
        // 含有非老頭牌
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 9)
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Honor.Red),
                        FakeIdentifiedTileFactory.create(Tile.Honor.Red),
                        FakeIdentifiedTileFactory.create(Tile.Honor.Red)
                    ),
                    sourceDirection = RelativeDirection.Left
                ),
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9))
                    ),
                    sourceDirection = RelativeDirection.Left
                ),
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 9))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 9)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val honroutouResult = result.yakuResults.find { it.yaku == YakuType.Honroutou }
        assertNull(honroutouResult, "Should not have Honroutou with non-routou tile")
    }
}