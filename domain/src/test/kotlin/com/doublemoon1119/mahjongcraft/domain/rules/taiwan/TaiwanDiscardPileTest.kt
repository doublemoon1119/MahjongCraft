package com.doublemoon1119.mahjongcraft.domain.rules.taiwan

import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.testing.domain.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.domain.table.DiscardPile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 針對 [TaiwanDiscardPile] 及其基礎行為進行單元測試。
 */
class TaiwanDiscardPileTest {

    /**
     * 驗證台灣麻將牌河的基礎存儲功能。
     */
    @Test
    fun `test taiwan discard pile basic storage`() {
        val pile = TaiwanDiscardPile()
        val tile = FakeIdentifiedTileFactory.create(Tile.Flower.Spring)

        // 台灣麻將僅使用基礎的 DiscardEntry
        val entry = DiscardPile.DiscardEntry(tile)
        pile.discard(entry)

        assertEquals(1, pile.entries.size)
        assertEquals(tile, pile.entries.first().tile)
    }

    /**
     * 驗證台灣麻將的鳴牌標記功能。
     */
    @Test
    fun `test taiwan takeLast behavior`() {
        val pile = TaiwanDiscardPile()
        val tile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9))

        pile.discard(DiscardPile.DiscardEntry(tile))

        // 模擬被碰走
        pile.takeLast()

        assertTrue(pile.entries.first().isTaken)
    }

    /**
     * 驗證在牌河為空時執行 takeLast 不應拋出異常。
     */
    @Test
    fun `test takeLast on empty taiwan pile`() {
        val pile = TaiwanDiscardPile()
        // 應安全執行
        pile.takeLast()
        assertEquals(0, pile.entries.size)
    }
}