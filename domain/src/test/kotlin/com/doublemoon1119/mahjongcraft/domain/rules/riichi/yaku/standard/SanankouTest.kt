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

/**
 * 三暗刻測試。
 *
 * 驗證三暗刻牌型之翻數計算正確性。
 */
class SanankouTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試三暗刻 - 門前清三暗刻。
     *
     * 手牌有三組暗刻，應獲得 2 翻。
     */
    @Test
    fun `test sanankou menzen`() {
        // 手牌：111m (暗刻), 222p (暗刻), 333s (暗刻), 77z, 7m (自摸)
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 7)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val sanankouResult = result.yakuResults.find { it.yaku == YakuType.Sanankou }
        assertEquals(2, sanankouResult?.han, "Sanankou should be 2 han")
    }

    /**
     * 測試三暗刻 - 有副露。
     *
     * 有副露時，手牌中仍需有三組暗刻，應獲得 2 翻。
     */
    @Test
    fun `test sanankou with fuuro`() {
        // 副露：碰 111m
        // 手牌：222p (暗刻), 333s (暗刻), 77z, 5s, 5s (湊成另一暗刻)
        // 手牌 10 張 + 副露 3 張 + 自摸 1 張 = 14 張
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 5)
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
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 5)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val sanankouResult = result.yakuResults.find { it.yaku == YakuType.Sanankou }
        assertEquals(2, sanankouResult?.han, "Sanankou should be 2 han even with fuuro")
    }

    /**
     * 測試三暗刻 - 含有暗槓。
     *
     * 暗槓也視為暗面子，應成立三暗刻。
     */
    @Test
    fun `test sanankou with ankan`() {
        // 副露：暗槓 111m
        // 手牌：222p (暗刻), 333s (暗刻), 77z, 66z (自摸湊成暗刻)
        // 暗槓 + 2 暗刻 + 1 暗刻（自摸）= 3 暗面子
        // 手牌 10 張 + 暗槓 4 張 + 自摸 1 張 = 15 張（含槓多一張）
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Green,
                Tile.Honor.Green
            ),
            melds = listOf(
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
                    ),
                    sourceDirection = RelativeDirection.Across
                )
            )
        )
        val winningTile = Tile.Honor.Green

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val sanankouResult = result.yakuResults.find { it.yaku == YakuType.Sanankou }
        assertEquals(2, sanankouResult?.han, "Sanankou should be 2 han with ankan")
    }

    /**
     * 測試三暗刻 - 不足三組暗刻。
     *
     * 手牌中暗刻不足三組時，應不成立三暗刻。
     */
    @Test
    fun `test sanankou with only two ankou returns null`() {
        // 手牌：111m (暗刻), 222p (暗刻), 123s (順子), 77z, 4s (自摸)
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 2),
                Tile.Numeric(Tile.Suit.Bamboo, 3),
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Numeric(Tile.Suit.Bamboo, 4)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 4)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val sanankouResult = result.yakuResults.find { it.yaku == YakuType.Sanankou }
        assertNull(sanankouResult, "Should not have Sanankou with only 2 ankou")
    }
}