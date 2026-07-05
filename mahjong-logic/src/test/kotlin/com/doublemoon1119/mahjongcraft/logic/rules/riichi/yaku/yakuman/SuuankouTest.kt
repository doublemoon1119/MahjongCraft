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
import kotlin.test.assertTrue

/**
 * 四暗刻與四暗刻單騎役種檢測測試。
 *
 * 測試以下役種：
 * - 四暗刻 (Suuankou) - 役滿
 * - 四暗刻單騎 (Suuankou Tanki) - 雙倍役滿
 *
 * 役種說明：
 * - 四暗刻：手牌由 4 個暗面子（暗刻或暗槓）組成，胡牌形成雀頭
 * - 四暗刻單騎：手牌由 4 個暗面子組成，胡牌形成第四個暗面子（單騎聽牌）
 *
 * @see calculateSuuankou
 * @see com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueCalculator
 */
class SuuankouTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試四暗刻 - 雙碰聽自摸。
     *
     * 手牌結構：
     * - 3 個暗刻：1m 1m 1m、9m 9m 9m、5s 5s 5s
     * - 雙碰聽：2p 2p、東 東
     * - 總共 14 張
     *
     * 胡牌：東（形成雀頭）
     */
    @Test
    fun `test suuankou with four concealed triplets`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 暗刻 1：1m 1m 1m
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                // 暗刻 2：9m 9m 9m
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                // 暗刻 3：5s 5s 5s
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                // 雙碰聽：2p 2p
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                // 雙碰聽：東 東
                Tile.Honor.East,
                Tile.Honor.East
            )
        )
        val winningTile = Tile.Honor.East

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-1, result.totalHan, "Total han should be -1 (yakuman)")
        assertTrue(result.yakuResults.any { it.yaku == YakuType.Suuankou }, "Should have Suuankou")
    }

    /**
     * 測試四暗刻 - 含暗槓。
     *
     * 手牌結構：
     * - 2 個暗刻：1m 1m 1m、9m 9m 9m
     * - 1 個暗槓：5s 5s 5s 5s
     * - 雙碰聽：2p 2p、東 東
     * - 總共 15 張
     */
    @Test
    fun `test suuankou with ankan`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 暗刻 1：1m 1m 1m
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                // 暗刻 2：9m 9m 9m
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                // 雙碰聽：2p 2p 2p
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                // 雙碰聽：東 東
                Tile.Honor.East,
                Tile.Honor.East
            ),
            melds = listOf(
                // 暗槓：5s 5s 5s 5s
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5))
                    ),
                    sourceDirection = RelativeDirection.Self
                )
            )
        )
        val winningTile = Tile.Honor.East

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertTrue(result.yakuResults.any { it.yaku == YakuType.Suuankou }, "Should have Suuankou")
    }

    /**
     * 測試四暗刻單騎 - 單騎聽牌。
     *
     * 手牌結構：
     * - 4 個暗刻：1m 1m 1m、9m 9m 9m、5s 5s 5s、2p 2p 2p
     * - 雀頭：發
     * - 總共 14 張
     *
     * 單騎等待：發
     * 胡牌：發
     */
    @Test
    fun `test suuankou tanki with single wait`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 暗刻 1：1m 1m 1m
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                // 暗刻 2：9m 9m 9m
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                // 暗刻 3：5s 5s 5s
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                // 暗刻 4：2p 2p 2p
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                // 雀頭：發（等待發）
                Tile.Honor.Green
            )
        )
        // 胡牌：發（單騎）
        val winningTile = Tile.Honor.Green

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertEquals(-2, result.totalHan, "Total han should be -2 (double yakuman)")
        assertTrue(result.yakuResults.any { it.yaku == YakuType.SuuankouTanki }, "Should have Suuankou Tanki")
    }

    /**
     * 測試非四暗刻 - 有副露（碰）。
     *
     * 手牌有副露，四暗刻不成立。
     */
    @Test
    fun `test non-suuankou with pon returns null`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 暗刻 1：1m 1m 1m
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                // 暗刻 2：9m 9m 9m
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                // 雙碰聽：2p 2p
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                // 雙碰聽：東 東
                Tile.Honor.East,
                Tile.Honor.East
            ),
            melds = listOf(
                // 副露（碰）：白 白 白
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Honor.White),
                        FakeIdentifiedTileFactory.create(Tile.Honor.White),
                        FakeIdentifiedTileFactory.create(Tile.Honor.White)
                    ),
                    sourceDirection = RelativeDirection.Across
                )
            )
        )
        val winningTile = Tile.Honor.East

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true, isMenzen = false)
        val result = calculator.calculate(context)

        assertTrue(result.yakuResults.none { it.yaku == YakuType.Suuankou }, "Should not have Suuankou with pon")
        assertTrue(
            result.yakuResults.none { it.yaku == YakuType.SuuankouTanki },
            "Should not have Suuankou Tanki with pon"
        )
    }

    /**
     * 測試非四暗刻 - 只有 3 個暗刻（形成三暗刻）。
     *
     * 手牌只有 3 個暗刻 + 1 個順子，四暗刻不成立。
     */
    @Test
    fun `test non-suuankou only three kotsu returns null`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 暗刻 1：1m 1m 1m
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                // 暗刻 2：9m 9m 9m
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                // 暗刻 3：5s 5s 5s
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                // 順子：2p 3p 4p（非暗刻！）
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 3),
                Tile.Numeric(Tile.Suit.Dot, 4),
                // 單騎：發
                Tile.Honor.Green
            )
        )
        val winningTile = Tile.Honor.Green

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.yakuResults.none { it.yaku == YakuType.Suuankou }, "Should not have Suuankou")
        assertTrue(result.yakuResults.none { it.yaku == YakuType.SuuankouTanki }, "Should not have Suuankou Tanki")
    }

    /**
     * 測試非四暗刻 - 雙碰聽非自摸。
     *
     * 手牌結構：
     * - 3 個暗刻：1m 1m 1m、9m 9m 9m、5s 5s 5s
     * - 雙碰聽：2p 2p、東 東
     * - 總共 14 張
     *
     * 榮和非自摸：東
     *
     * 此時為非四暗刻，而是三暗刻+對對和的形式。
     */
    @Test
    fun `test suuankou non-tanki with head formation`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 暗刻 1：1m 1m 1m
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                // 暗刻 2：9m 9m 9m
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                // 暗刻 3：5s 5s 5s
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                Tile.Numeric(Tile.Suit.Bamboo, 5),
                // 雙碰聽：2p 2p
                Tile.Numeric(Tile.Suit.Dot, 2),
                Tile.Numeric(Tile.Suit.Dot, 2),
                // 雙碰聽：東 東
                Tile.Honor.East,
                Tile.Honor.East
            )
        )
        // 胡牌：東
        val winningTile = Tile.Honor.East

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = false)
        val result = calculator.calculate(context)

        assertTrue(result.yakuResults.none { it.yaku == YakuType.Suuankou }, "Should not have Suuankou")
        assertTrue(result.yakuResults.none { it.yaku == YakuType.SuuankouTanki }, "Should not have Suuankou Tanki")
        assertTrue(result.yakuResults.any { it.yaku == YakuType.Sanankou }, "Should have Sanankou")
        assertTrue(result.yakuResults.any { it.yaku == YakuType.Toitoi }, "Should have Toitoi")
    }
}
