package com.doublemoon1119.mahjongcraft.domain.riichi

import com.doublemoon1119.mahjongcraft.domain.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.domain.base.Tile
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 針對 [RiichiDiscardPile] 及其特有邏輯進行單元測試。
 */
class RiichiDiscardPileTest {

    /**
     * 驗證日本麻將牌河是否能正確存儲並識別立直捨牌。
     */
    @Test
    fun `test riichi discard entry properties`() {
        val pile = RiichiDiscardPile()
        val tile = IdentifiedTile(UUID.randomUUID(), Tile.Honor.East)

        // 建立並放入立直捨牌紀錄
        val entry = RiichiDiscardEntry(tile, isRiichi = true)
        pile.discard(entry)

        assertEquals(1, pile.entries.size)
        // 驗證泛型是否允許直接存取 RiichiDiscardEntry 特有屬性
        assertTrue(pile.entries.first().isRiichi)
        assertFalse(pile.entries.first().isTaken)
    }

    /**
     * 驗證鳴牌邏輯是否正確作用於最後一張紀錄。
     */
    @Test
    fun `test riichi takeLast behavior`() {
        val pile = RiichiDiscardPile()
        val tile1 = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 1))
        val tile2 = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2))

        pile.discard(RiichiDiscardEntry(tile1))
        pile.discard(RiichiDiscardEntry(tile2))

        // 模擬第二張牌被鳴走
        pile.takeLast()

        assertTrue(pile.entries[1].isTaken)
        assertFalse(pile.entries[0].isTaken)
    }
}