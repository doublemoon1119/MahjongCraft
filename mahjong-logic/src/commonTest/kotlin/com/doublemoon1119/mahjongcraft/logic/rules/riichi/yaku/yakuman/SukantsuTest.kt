package com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.yakuman

import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.RiichiHandValueCalculatorTestBase
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.FakeRiichiHandValueContextFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 四杠子役種檢測測試。
 *
 * 測試以下役種：
 * - 四杠子 (Sukantsu) - 役滿
 *
 * 四杠子的手牌結構必須是「4 面子 + 1 雀頭」，且這 4 個面子全部都是槓子。
 *
 * @see calculateSukantsu
 * @see com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueCalculator
 */
class SukantsuTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試四杠子 - 3 個暗槓 + 1 個明槓。
     *
     * 手牌結構：
     * - 3 個暗槓
     * - 1 個明槓
     * - 1 個雀頭
     * - 總共 14 張
     */
    @Test
    fun `test suukantsu with three ankan and one minkan`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 單騎：東
                Tile.Honor.East,
            ),
            melds = listOf(
                // 暗槓 1：1m 1m 1m 1m
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                    ),
                    sourceDirection = RelativeDirection.Self,
                ),
                // 暗槓 2：9m 9m 9m 9m
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                    ),
                    sourceDirection = RelativeDirection.Self,
                ),
                // 暗槓 3：5s 5s 5s 5s
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                    ),
                    sourceDirection = RelativeDirection.Self,
                ),
                // 明槓 4：發 發 發 發
                Meld(
                    type = MeldType.OPEN_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Honor.Green),
                        FakeIdentifiedTileFactory.create(Tile.Honor.Green),
                        FakeIdentifiedTileFactory.create(Tile.Honor.Green),
                        FakeIdentifiedTileFactory.create(Tile.Honor.Green),
                    ),
                    sourceDirection = RelativeDirection.Across,
                ),
            ),
        )
        val winningTile = Tile.Honor.East

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = false, isMenzen = false)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-1, result.totalHan, "Total han should be -1 (yakuman)")
        assertTrue(result.yakuResults.any { it.yaku == YakuType.Sukantsu }, "Should have Sukantsu")
    }

    /**
     * 測試四杠子 - 4 個暗槓。
     *
     * 手牌結構：
     * - 4 個暗槓
     * - 1 個雀頭
     * - 總共 18 張
     */
    @Test
    fun `test suukantsu with four ankan`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 單騎：東
                Tile.Honor.East,
            ),
            melds = listOf(
                // 暗槓 1：1m 1m 1m 1m
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                    ),
                    sourceDirection = RelativeDirection.Self,
                ),
                // 暗槓 2：9m 9m 9m 9m
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                    ),
                    sourceDirection = RelativeDirection.Self,
                ),
                // 暗槓 3：5s 5s 5s 5s
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                    ),
                    sourceDirection = RelativeDirection.Self,
                ),
                // 暗槓 4：發 發 發 發
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Honor.Green),
                        FakeIdentifiedTileFactory.create(Tile.Honor.Green),
                        FakeIdentifiedTileFactory.create(Tile.Honor.Green),
                        FakeIdentifiedTileFactory.create(Tile.Honor.Green),
                    ),
                    sourceDirection = RelativeDirection.Self,
                ),
            ),
        )
        val winningTile = Tile.Honor.East

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertTrue(result.yakuResults.any { it.yaku == YakuType.Sukantsu }, "Should have Sukantsu")
    }

    /**
     * 測試非四杠子 - 只有三個槓。
     *
     * 只有 3 個槓，應為三杠子而非四杠子。
     */
    @Test
    fun `test non-sukantsu with only three kans returns null`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 面子：2p 3p 4p
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                // 單騎：東
                Tile.Honor.East,
            ),
            melds = listOf(
                // 暗槓 1：1m 1m 1m 1m
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                    ),
                    sourceDirection = RelativeDirection.Self,
                ),
                // 暗槓 2：9m 9m 9m 9m
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                    ),
                    sourceDirection = RelativeDirection.Self,
                ),
                // 暗槓 3：5s 5s 5s 5s
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                    ),
                    sourceDirection = RelativeDirection.Self,
                ),
            ),
        )
        val winningTile = Tile.Honor.East

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        assertNull(
            result.yakuResults.find { it.yaku == YakuType.Sukantsu },
            "Should not have Sukantsu with only 3 kans",
        )
        assertTrue(result.yakuResults.any { it.yaku == YakuType.Sankantsu }, "Should have Sankantsu with 3 kans")
    }

    /**
     * 測試非四杠子 - 4 個面子但其中有非槓。
     *
     * 有 4 個面子，但其中有順子，不是全部都是槓子。
     */
    @Test
    fun `test non-sukantsu with non-kan mentsu returns null`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 面子：2p 3p 4p
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                // 單騎：東
                Tile.Honor.East,
            ),
            melds = listOf(
                // 暗槓 1：1m 1m 1m 1m
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)),
                    ),
                    sourceDirection = RelativeDirection.Self,
                ),
                // 暗槓 2：9m 9m 9m 9m
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9)),
                    ),
                    sourceDirection = RelativeDirection.Self,
                ),
                // 明槓 3：發 發 發 發
                Meld(
                    type = MeldType.OPEN_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Honor.Green),
                        FakeIdentifiedTileFactory.create(Tile.Honor.Green),
                        FakeIdentifiedTileFactory.create(Tile.Honor.Green),
                        FakeIdentifiedTileFactory.create(Tile.Honor.Green),
                    ),
                    sourceDirection = RelativeDirection.Across,
                ),
            ),
        )
        val winningTile = Tile.Honor.East

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = false, isMenzen = false)
        val result = calculator.calculate(context)

        assertNull(
            result.yakuResults.find { it.yaku == YakuType.Sukantsu },
            "Should not have Sukantsu with non-kan mentsu",
        )
    }
}
