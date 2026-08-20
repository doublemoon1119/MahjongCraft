package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.structure.Mentsu
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.FakeRiichiHandValueContextFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * [RiichiHandValueCalculator] 對吃牌副露 [Meld.tiles] 順序的迴歸測試。
 *
 * `Meld.tiles` 依鳴牌時呼叫端選牌順序排列（被鳴牌固定最後一張），不保證數值遞增——例如吃 6 索用
 * 7、8 索，`withTiles` 若以 `[8, 7]` 順序傳入，`tiles` 就會是 `[8, 7, 6]`，`tiles.first()` 不是這組
 * 順子裡數值最小的那張。實際遊戲內曾因此在算番階段把最大值當成 [Mentsu.Shuntsu] 的 `headTile`，
 * 導致 `headTile.value + 2` 超出 1~9 範圍而拋出 `IllegalArgumentException`。
 */
class RiichiHandValueCalculatorChiMeldOrderingTest : RiichiHandValueCalculatorTestBase() {

    @Test
    fun `test chi meld with descending tile order does not crash and uses correct shuntsu`() {
        // 副露：吃 6 索（用 8、7 索，tiles 依此順序排列，數值遞減）
        // 手牌：234m (順子), 234p (順子), 55s (雀頭), 67p (餘牌)，胡 8p 組成 678p
        // 全部都是簡張（2~8），開立且無字牌／老頭牌，應成立斷么九 (Tanyao) 1 翻
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 6),
                Tile.Numeric(Tile.Suit.Dot, 7),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
            ),
            melds = listOf(
                Meld(
                    type = MeldType.CHI,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 8)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 7)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 6)),
                    ),
                    sourceDirection = RelativeDirection.Left,
                ),
            ),
        )
        val winningTile = Tile.Numeric(Tile.Suit.Dot, 8)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = false, isMenzen = false)
        val result = calculator.calculate(context)

        val tanyaoResult = result.yakuResults.find { it.yaku == YakuType.Tanyao }
        assertNotNull(tanyaoResult, "Should have Tanyao once the descending-order chi meld is decomposed correctly")
        assertEquals(1, tanyaoResult.han, "Open Tanyao should be 1 han")
    }
}
