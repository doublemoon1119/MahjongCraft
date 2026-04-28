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

/**
 * 三色同刻 (Sanshoku Dokoku) 役種測試。
 *
 * 測試三個相同數字但不同花色的刻子是否正確計算為 2 翻。
 *
 * @see com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandValueCalculator
 */
class SanshokuDokokuTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試三色同刻 - 門前清。
     *
     * 手牌有三個相同數字但不同花色的刻子，應獲得 2 翻。
     */
    @Test
    fun `test sanshoku dokoku menzen`() {
        // 手牌：111m, 111p, 111s, 77z, 56m (自摸)
        // 3 刻子 + 1 雀頭
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
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 7)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val sanshokuDokokuResult = result.yakuResults.find { it.yaku == YakuType.SanshokuDokoku }
        assertEquals(2, sanshokuDokokuResult?.han, "SanshokuDokoku should be 2 han")
    }

    /**
     * 測試三色同刻 - 有副露。
     *
     * 有副露時仍可成立三色同刻，應獲得 2 翻。
     */
    @Test
    fun `test sanshoku dokoku with fuuro`() {
        // 副露：碰 111m
        // 手牌：111p, 111s, 77z, 66z (自摸湊成刻子)
        // 副露 1 刻子 + 手牌 2 刻子 = 3 刻子
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Green,
                Tile.Honor.Green
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
        val winningTile = Tile.Honor.Green

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val sanshokuDokokuResult = result.yakuResults.find { it.yaku == YakuType.SanshokuDokoku }
        assertEquals(2, sanshokuDokokuResult?.han, "SanshokuDokoku should be 2 han even with fuuro")
    }

    /**
     * 測試三色同刻 - 不足三組。
     *
     * 只有兩組相同數字的刻子時，應不成立三色同刻。
     */
    @Test
    fun `test sanshoku dokoku with only two pungs returns null`() {
        // 手牌：111m, 111p, 234s, 77z, 66s (自摸)
        // 只有 2 組三色同刻的要素
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Dot, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 4),
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Numeric(Tile.Suit.Bamboo, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 6)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 6)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val sanshokuDokokuResult = result.yakuResults.find { it.yaku == YakuType.SanshokuDokoku }
        assertNull(sanshokuDokokuResult, "Should not have SanshokuDokoku with only 2 pungs")
    }
}