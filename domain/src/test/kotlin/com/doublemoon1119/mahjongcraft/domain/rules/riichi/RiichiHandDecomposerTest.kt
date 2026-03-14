package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.Fuuro
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.HandStructure
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.structure.Mentsu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [RiichiHandDecomposer] 的單元測試。
 *
 * 測試手牌分割功能，包括標準手牌、七對子（Chiitoitsu）、國士無雙（KokushiMusou）等牌型。
 */
class RiichiHandDecomposerTest {

    /**
     * 測試不完整的手牌無法分割。
     *
     * 牌數不足 14 張時，應返回 null。
     */
    @Test
    fun `test invalid hand returns null`() {
        val tiles = listOf(
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 2),
            Tile.Numeric(Tile.Suit.Character, 3)
        )

        val result = RiichiHandDecomposer.decompose(tiles)

        assertNull(result)
    }

    /**
     * 測試非 14 張牌無法分割。
     */
    @Test
    fun `test not 14 tiles returns null`() {
        val tiles = listOf(
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 1)
        )

        val result = RiichiHandDecomposer.decompose(tiles)

        assertNull(result)
    }

    /**
     * 測試國士無雙（KokushiMusou）沒有對子時無法分割。
     *
     * 國士無雙需要十三張不同的么九牌加上一張重複的牌做雀頭。
     */
    @Test
    fun `test kokushi musou without pair returns null`() {
        val tiles = listOf(
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 9),
            Tile.Numeric(Tile.Suit.Dot, 1),
            Tile.Numeric(Tile.Suit.Dot, 9),
            Tile.Numeric(Tile.Suit.Bamboo, 1),
            Tile.Numeric(Tile.Suit.Bamboo, 9),
            Tile.Honor.East,
            Tile.Honor.South,
            Tile.Honor.West,
            Tile.Honor.North,
            Tile.Honor.Red,
            Tile.Honor.Green,
            Tile.Honor.White,
            Tile.Numeric(Tile.Suit.Character, 2)
        )

        val result = RiichiHandDecomposer.decompose(tiles)

        assertNull(result)
    }

    /**
     * 測試七對子（Chiitoitsu）不能有副露。
     *
     * 七對子為門前清牌型，不能有副露。
     */
    @Test
    fun `test chiitoitsu cannot have fuuro`() {
        val tiles = listOf(
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 2),
            Tile.Numeric(Tile.Suit.Character, 2),
            Tile.Numeric(Tile.Suit.Character, 3),
            Tile.Numeric(Tile.Suit.Character, 3),
            Tile.Numeric(Tile.Suit.Character, 4),
            Tile.Numeric(Tile.Suit.Character, 4),
            Tile.Numeric(Tile.Suit.Character, 5),
            Tile.Numeric(Tile.Suit.Character, 5),
            Tile.Numeric(Tile.Suit.Character, 6),
            Tile.Numeric(Tile.Suit.Character, 6),
            Tile.Numeric(Tile.Suit.Character, 7),
            Tile.Numeric(Tile.Suit.Character, 7)
        )

        val fuuro = listOf(
            Fuuro(
                mentsu = Mentsu.Kotsu(Tile.Numeric(Tile.Suit.Dot, 1)),
                from = RelativeDirection.Left
            )
        )

        val result = RiichiHandDecomposer.decompose(tiles, fuuro)

        assertNull(result)
    }

    /**
     * 測試七對子（Chiitoitsu）分割。
     *
     * 使用不同花色的牌，確保無法組成標準手牌。
     */
    @Test
    fun `test chiitoitsu decomposition`() {
        val tiles = listOf(
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Dot, 2),
            Tile.Numeric(Tile.Suit.Dot, 2),
            Tile.Numeric(Tile.Suit.Bamboo, 3),
            Tile.Numeric(Tile.Suit.Bamboo, 3),
            Tile.Numeric(Tile.Suit.Character, 5),
            Tile.Numeric(Tile.Suit.Character, 5),
            Tile.Numeric(Tile.Suit.Dot, 6),
            Tile.Numeric(Tile.Suit.Dot, 6),
            Tile.Numeric(Tile.Suit.Bamboo, 7),
            Tile.Numeric(Tile.Suit.Bamboo, 7),
            Tile.Numeric(Tile.Suit.Character, 9),
            Tile.Numeric(Tile.Suit.Character, 9)
        )

        val result = RiichiHandDecomposer.decompose(tiles)

        assertNotNull(result)
        assertTrue(result is HandStructure.Chiitoitsu)
        assertEquals(7, result.pairs.size)
    }

    /**
     * 測試碰碰胡（Toitoi）分割。
     *
     * 全部由刻子組成的牌型。
     */
    @Test
    fun `test toitoi hand decomposition`() {
        val tiles = listOf(
            Tile.Honor.White,
            Tile.Honor.White,
            Tile.Honor.White,
            Tile.Honor.East,
            Tile.Honor.East,
            Tile.Honor.East,
            Tile.Honor.Green,
            Tile.Honor.Green,
            Tile.Honor.Green,
            Tile.Honor.Red,
            Tile.Honor.Red,
            Tile.Honor.Red,
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 1)
        )

        val result = RiichiHandDecomposer.decompose(tiles)

        assertNotNull(result)
        assertTrue(result is HandStructure.Standard)

        assertEquals(4, result.mentsus.size)
        assertTrue(result.mentsus.all { it is Mentsu.Kotsu })

        assertTrue(result.pair.tile is Tile.Numeric)
    }

    /**
     * 測試包含順子的標準手牌分割。
     *
     * 驗證手牌可以正確分割為 3 個順子 + 1 個刻子 + 1 個雀頭。
     */
    @Test
    fun `test standard hand with sequences decomposition`() {
        val tiles = listOf(
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 2),
            Tile.Numeric(Tile.Suit.Character, 3),
            Tile.Numeric(Tile.Suit.Character, 4),
            Tile.Numeric(Tile.Suit.Character, 4),
            Tile.Numeric(Tile.Suit.Character, 5),
            Tile.Numeric(Tile.Suit.Character, 6),
            Tile.Numeric(Tile.Suit.Character, 7),
            Tile.Numeric(Tile.Suit.Character, 8),
            Tile.Numeric(Tile.Suit.Character, 9),
            Tile.Honor.Red,
            Tile.Honor.Red,
            Tile.Honor.Red
        )

        val result = RiichiHandDecomposer.decompose(tiles)

        assertNotNull(result)
        assertTrue(result is HandStructure.Standard)

        assertEquals(4, result.mentsus.size)

        val shuntsuCount = result.mentsus.count { it is Mentsu.Shuntsu }
        val kotsuCount = result.mentsus.count { it is Mentsu.Kotsu }
        assertEquals(1, kotsuCount)
        assertEquals(3, shuntsuCount)

        assertTrue(result.pair.tile is Tile.Numeric)
    }
}
