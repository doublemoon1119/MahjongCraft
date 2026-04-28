package com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.yakuman

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 清老頭 (Chinroutou) 役種檢測測試。
 *
 * 測試以下役種：
 * - 清老頭 (Chinroutou) - 役滿
 *
 * @see calculateChinroutou
 * @see com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiHandValueCalculator
 */
class ChinroutouTest : RiichiHandValueCalculatorTestBase() {

    /**
     * 測試清老頭 - 完整手牌不含字牌。
     *
     * 手牌：111m, 999p, 111s, 999s, 9m
     * 胡牌：9m
     *
     * 清老頭：役滿（全部為老頭牌 1、9，不含字牌）
     */
    @Test
    fun `test chinroutou complete hand`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 1m 刻子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                // 9p 刻子
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Numeric(Tile.Suit.Dot, 9),
                // 1s 刻子
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                // 9s 刻子
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                // 9m 雀頭
                Tile.Numeric(Tile.Suit.Character, 9)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 9)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertTrue(
            result.yakuResults.any { it.yaku == YakuType.Chinroutou },
            "Should contain Chinroutou, got: ${result.yakuResults.map { it.yaku }}"
        )
    }

    /**
     * 測試清老頭 - 含副露。
     *
     * 手牌：999m, 999p, 11s, 99s
     * 副露：111s
     * 胡牌：9s
     *
     * 清老頭：役滿（全部為老頭牌 1、9，不含字牌）
     */
    @Test
    fun `test chinroutou with fuuro`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 9m 刻子
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                // 9p 刻子
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Numeric(Tile.Suit.Dot, 9),
                // 1s 雀頭
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                // 9s 等待牌
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 9)
            ),
            melds = listOf(
                // 副露：1s 刻子
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1))
                    ),
                    sourceDirection = RelativeDirection.Across
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Bamboo, 9)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertTrue(
            result.yakuResults.any { it.yaku == YakuType.Chinroutou },
            "Should contain Chinroutou, got: ${result.yakuResults.map { it.yaku }}"
        )
    }

    /**
     * 測試清老頭 - 含暗槓。
     *
     * 手牌：999m, 99p, 111s, 11m
     * 副露：1111p(暗槓)
     * 胡牌：9p
     *
     * 清老頭：役滿（全部為老頭牌 1、9，不含字牌）
     */
    @Test
    fun `test chinroutou with ankan`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 9m 刻子
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                // 9p 雀頭
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Numeric(Tile.Suit.Dot, 9),
                // 1s 刻子
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                // 1m 雀頭
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1)
            ),
            melds = listOf(
                // 暗槓：1p
                Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = listOf(
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)),
                        FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
                    ),
                    sourceDirection = RelativeDirection.Self
                )
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Dot, 9)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(result.isYakuman, "Should be yakuman")
        assertTrue(
            result.yakuResults.any { it.yaku == YakuType.Chinroutou },
            "Should contain Chinroutou, got: ${result.yakuResults.map { it.yaku }}"
        )
    }

    /**
     * 測試不是清老頭 - 含有字牌。
     *
     * 手牌：111m, 999m, 999p, 11s、中中
     * 胡牌：1m
     *
     * 此手牌應為混老頭而非清老頭。
     */
    @Test
    fun `test not chinroutou with honor tile`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 1m 刻子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                // 9m 刻子
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9),
                // 9p 刻子
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Numeric(Tile.Suit.Dot, 9),
                // 1s 雀頭
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                // 中 對子
                Tile.Honor.Red,
                Tile.Honor.Red
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 1)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertFalse(
            result.yakuResults.any { it.yaku == YakuType.Chinroutou },
            "Should not contain Chinroutou when hand contains honor tiles, got: ${result.yakuResults.map { it.yaku }}"
        )
    }

    /**
     * 測試不是清老頭 - 含有非老頭牌。
     *
     * 手牌：111m, 777p, 111s, 999s, 9m
     * 胡牌：m
     *
     * 此手牌含有 7p，不是老頭牌。
     */
    @Test
    fun `test not chinroutou with non-terminal tile`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 1m 刻子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                // 7p 刻子（非老頭牌）
                Tile.Numeric(Tile.Suit.Dot, 7),
                Tile.Numeric(Tile.Suit.Dot, 7),
                Tile.Numeric(Tile.Suit.Dot, 7),
                // 1s 刻子
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                // 9s 刻子
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                // 9m 雀頭
                Tile.Numeric(Tile.Suit.Character, 9)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 9)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertFalse(
            result.yakuResults.any { it.yaku == YakuType.Chinroutou },
            "Should not contain Chinroutou when hand contains non-terminal tiles, got: ${result.yakuResults.map { it.yaku }}"
        )
    }

    /**
     * 測試清老頭與混老頭的互斥性。
     *
     * 當手牌為清老頭時，應只觸發清老頭而非混老頭。
     */
    @Test
    fun `test chinroutou excludes honroutou`() {
        val hand = FakeHandFactory.create(
            listOf(
                // 1m 刻子
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                Tile.Numeric(Tile.Suit.Character, 1),
                // 9p 刻子
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Numeric(Tile.Suit.Dot, 9),
                Tile.Numeric(Tile.Suit.Dot, 9),
                // 1s 刻子
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                Tile.Numeric(Tile.Suit.Bamboo, 1),
                // 9s 雀頭
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                // 9m 雀頭
                Tile.Numeric(Tile.Suit.Character, 9),
                Tile.Numeric(Tile.Suit.Character, 9)
            )
        )
        val winningTile = Tile.Numeric(Tile.Suit.Character, 9)

        val context = FakeRiichiHandValueContextFactory.create(hand, winningTile, isTsumo = true)
        val result = calculator.calculate(context)

        assertTrue(
            result.yakuResults.any { it.yaku == YakuType.Chinroutou },
            "Should contain Chinroutou"
        )
        assertFalse(
            result.yakuResults.any { it.yaku == YakuType.Honroutou },
            "Should not contain Honroutou when hand is Chinroutou"
        )
    }
}
