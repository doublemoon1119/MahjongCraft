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
import kotlin.test.assertNull

/**
 * 斷么九 (Tanyao) 役種測試。
 *
 * @see com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandValueCalculator
 */
class TanyaoTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試斷么九 - 門前清。
     *
     * 手牌僅有 2-8 數牌，應獲得 1 翻。
     */
    @Test
    fun `test tanyao menzen`() {
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 6),
                Tile.Numeric(Tile.Suit.Dot, 7)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 2)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val tanyaoResult = result.yakuResults.find { it.yaku == YakuType.Tanyao }
        assertEquals(1, tanyaoResult?.han, "Tanyao should be 1 han")
    }

    /**
     * 測試斷么九 - 有么九牌。
     *
     * 手牌包含 1m，應無法獲得斷么九。
     */
    @Test
    fun `test tanyao with terminal`() {
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 6),
                Tile.Numeric(Tile.Suit.Dot, 7)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 2)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val tanyaoResult = result.yakuResults.find { it.yaku == YakuType.Tanyao }
        assertNull(tanyaoResult, "Should not have Tanyao with terminal tile")
    }

    /**
     * 測試斷么九 - 有字牌。
     *
     * 手牌包含字牌，應無法獲得斷么九。
     */
    @Test
    fun `test tanyao with honor`() {
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Honor.East,
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 6)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 2)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        val tanyaoResult = result.yakuResults.find { it.yaku == YakuType.Tanyao }
        assertNull(tanyaoResult, "Should not have Tanyao with honor tile")
    }

    /**
     * 測試斷么九 - 有副露。
     *
     * 有副露時手牌仍能正確分解為「副露 + 手牌」，並計算斷么九。
     * 此測試驗證 [com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandDecomposer.tryDecomposeStandard] 正確處理副露。
     */
    @Test
    fun `test tanyao with pon fuuro`() {
        // 副露：碰 555m
        // 手牌：234m, 678m, 55m, 自摸 2m = 10張 + 1張 = 14張
        // 手牌與副露全部為 2-8 數牌，應獲得 1 翻（斷么九）
        val hand = createHand(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Character, 6),
                Tile.Numeric(Tile.Suit.Character, 7),
                Tile.Numeric(Tile.Suit.Character, 8),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 5),
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 2)
            ),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5)),
                        IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Character, 5))
                    ),
                    sourceDirection = RelativeDirection.Left
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 5)

        val context = createContext(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        val tanyaoResult = result.yakuResults.find { it.yaku == YakuType.Tanyao }
        assertEquals(1, tanyaoResult?.han, "Tanyao should be 1 han even with fuuro")
    }
}