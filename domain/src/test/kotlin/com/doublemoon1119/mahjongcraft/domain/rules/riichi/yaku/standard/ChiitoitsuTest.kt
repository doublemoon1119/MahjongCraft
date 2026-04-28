package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.domain.base.Meld
import com.doublemoon1119.mahjongcraft.domain.base.MeldType
import com.doublemoon1119.mahjongcraft.domain.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.fakes.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.domain.fakes.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.domain.fakes.rules.riichi.FakeRiichiHandValueContextFactory
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChiitoitsuTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試七對子 - 門前清。
     *
     * 手牌為七個對子，應獲得 2 翻。
     */
    @Test
    fun `test chiitoitsu menzen`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Dot, 7)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Dot, 7)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val chiitoitsuResult = result.yakuResults.find { it.yaku == YakuType.Chiitoitsu }
        assertEquals(2, chiitoitsuResult?.han, "Chiitoitsu should be 2 han for menzen")
    }

    /**
     * 測試七對子 - 有副露（非門前清）。
     *
     * 七對子必須為門前清，有副露時應不成立。
     */
    @Test
    fun `test chiitoitsu with fuuro returns null`() {
        // 七對子手牌：13 張手牌 + 1 張自摸 = 7 對子
        // 其中一對被副露消耗（副露 3 張 + 手牌 10 張 + 摸牌 1 張 = 14 張）
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Dot, 7),
                Tile.Numeric(Tile.Suit.Dot, 7)
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val chiitoitsuResult = result.yakuResults.find { it.yaku == YakuType.Chiitoitsu }
        assertNull(chiitoitsuResult, "Chiitoitsu should not be present when there is fuuro")
    }

    /**
     * 測試七對子 - 非七對子牌型。
     *
     * 一般手牌應不成立七對子役種。
     */
    @Test
    fun `test chiitoitsu with more than seven pairs returns null`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 7),
                Tile.Numeric(Tile.Suit.Dot, 8),
                Tile.Numeric(Tile.Suit.Dot, 9)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val chiitoitsuResult = result.yakuResults.find { it.yaku == YakuType.Chiitoitsu }
        assertNull(chiitoitsuResult, "Non-chiitoitsu hand should not have chiitoitsu yaku")
    }
}