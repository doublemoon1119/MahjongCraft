package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.standard

import com.doublemoon1119.mahjongcraft.domain.base.Meld
import com.doublemoon1119.mahjongcraft.domain.base.MeldType
import com.doublemoon1119.mahjongcraft.domain.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.fakes.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.domain.fakes.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.YakuType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToitoiTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試對對胡 - 純手牌（門前清）。
     *
     * 手牌為四組刻子 + 一組雀頭，應獲得 2 翻。
     */
    @Test
    fun `test toitoi menzen`() {
        // 手牌：111m, 222m, 333p, 44s, 77z
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Honor.Red,
                Tile.Honor.Red
            )
        )
        val winningTile = Tile.Honor.Red

        val context = createContext(hand, winningTile, isTsumo = false, isMenzen = true)
        val result = calculator.calculate(context)

        val toitoiResult = result.yakuResults.find { it.yaku == YakuType.Toitoi }
        assertEquals(2, toitoiResult?.han, "Toitoi should be 2 han")
    }

    /**
     * 測試對對胡 - 有副露。
     *
     * 有副露時仍可成立對對胡，此為與七對子之差異。
     */
    @Test
    fun `test toitoi with fuuro`() {
        // 副露：碰 111m (1 組刻子)
        // 手牌：222m, 333p, 444s, 77z (3 組刻子 + 1 雀頭)
        // 手牌 10 張 + 副露 3 張 + 自摸 1 張 = 14 張
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Honor.Red
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
        val winningTile = Tile.Honor.Red

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val toitoiResult = result.yakuResults.find { it.yaku == YakuType.Toitoi }
        assertEquals(2, toitoiResult?.han, "Toitoi should be 2 han even with fuuro")
    }

    /**
     * 測試對對胡 - 包含槓。
     *
     * 手牌包含明槓或暗槓時，仍可成立對對胡。
     */
    @Test
    fun `test toitoi with kan`() {
        // 副露：明槓 111m (1 組槓)
        // 手牌：222m, 333p, 444s, 77z (3 組刻子 + 1 雀頭)
        // 手牌 10 張 + 副露 4 張 + 自摸 1 張 = 15 張（含槓多一張）
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Honor.Red
            ),
            melds = listOf(
                Meld(
                    type = MeldType.OPEN_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Honor.Red

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val toitoiResult = result.yakuResults.find { it.yaku == YakuType.Toitoi }
        assertEquals(2, toitoiResult?.han, "Toitoi should be 2 han with kan")
    }

    /**
     * 測試對對胡 - 含有順子。
     *
     * 手牌含有順子時，應不成立對對胡。
     */
    @Test
    fun `test toitoi with shuntsu returns null`() {
        // 手牌：123m (順子), 111m, 222p, 333s, 77z
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Honor.Red
            )
        )
        val winningTile = Tile.Honor.Red

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val toitoiResult = result.yakuResults.find { it.yaku == YakuType.Toitoi }
        assertNull(toitoiResult, "Should not have Toitoi with shuntsu")
    }
}
