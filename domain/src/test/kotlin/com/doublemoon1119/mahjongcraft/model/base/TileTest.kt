package com.doublemoon1119.mahjongcraft.model.base

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 針對 [Tile] 領域模型進行單元測試。
 * 驗證模型建立的正確性與邊界條件限制。
 */
class TileTest {

    /**
     * 測試正常的數牌建立是否正確存儲花色與數值。
     */
    @Test
    fun `test valid numeric tile creation`() {
        val tile = Tile.Numeric(Tile.Suit.Character, 5)
        assertEquals(Tile.Suit.Character, tile.suit)
        assertEquals(5, tile.value)
    }

    /**
     * 測試字牌的單例屬性。
     */
    @Test
    fun `test honor tile objects`() {
        val east1 = Tile.Honor.East
        val east2 = Tile.Honor.East
        // 驗證 East 是單例物件 (Object)
        assertEquals(east1, east2)
    }

    /**
     * 測試邊界條件：驗證當數牌數值小於 1 時應拋出異常。
     */
    @Test
    fun `test invalid numeric tile value too low`() {
        assertFailsWith<IllegalArgumentException> {
            Tile.Numeric(Tile.Suit.Dot, 0)
        }
    }

    /**
     * 測試邊界條件：驗證當數牌數值大於 9 時應拋出異常。
     */
    @Test
    fun `test invalid numeric tile value too high`() {
        assertFailsWith<IllegalArgumentException> {
            Tile.Numeric(Tile.Suit.Bamboo, 10)
        }
    }
}