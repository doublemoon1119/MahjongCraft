package com.doublemoon1119.mahjongcraft.model

import com.doublemoon1119.mahjongcraft.model.riichi.RiichiTileOrder
import com.doublemoon1119.mahjongcraft.model.taiwan.TaiwanTileOrder
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HandTest {

    @Test
    fun `test drawing and discarding by id`() {
        val id1 = UUID.randomUUID()
        val tile1 = IdentifiedTile(id1, Tile.Numeric(Tile.Suit.Dots, 1))
        val hand = Hand(mutableListOf(tile1))

        val id2 = UUID.randomUUID()
        val tile2 = IdentifiedTile(id2, Tile.Numeric(Tile.Suit.Dots, 2))

        hand.draw(tile2)
        assertEquals(tile2, hand.lastDrawn)
        assertEquals(2, hand.size)

        // 模擬玩家點擊實體 UUID 進行打牌
        val discarded = hand.discardById(id2)
        assertEquals(tile2, discarded)
        assertNull(hand.lastDrawn)
        assertEquals(1, hand.size)
    }

    @Test
    fun `test sorting with different regional orders`() {
        val red = IdentifiedTile(UUID.randomUUID(), Tile.Honor.Red)     // 中
        val white = IdentifiedTile(UUID.randomUUID(), Tile.Honor.White) // 白
        val hand = Hand(mutableListOf(red, white))

        // 測試日麻排序 (白在前)
        hand.organize(RiichiTileOrder)
        assertEquals(white, hand.allTiles[0])

        // 測試台麻排序 (中在前)
        hand.organize(TaiwanTileOrder)
        assertEquals(red, hand.allTiles[0])
    }
}