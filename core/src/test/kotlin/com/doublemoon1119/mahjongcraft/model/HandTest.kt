package com.doublemoon1119.mahjongcraft.model

import com.doublemoon1119.mahjongcraft.model.riichi.RiichiTileOrder
import com.doublemoon1119.mahjongcraft.model.taiwan.TaiwanTileOrder
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 針對 [Hand] 的基礎功能進行單元測試。
 */
class HandTest {

    /**
     * 測試摸牌與透過 UUID 捨牌的邏輯。
     */
    @Test
    fun `test drawing and discarding by id`() {
        val id1 = UUID.randomUUID()
        val tile1 = IdentifiedTile(id1, Tile.Numeric(Tile.Suit.Dot, 1))
        val hand = Hand(mutableListOf(tile1))

        val id2 = UUID.randomUUID()
        val tile2 = IdentifiedTile(id2, Tile.Numeric(Tile.Suit.Dot, 2))

        // 摸牌
        hand.lastDrawn = tile2
        assertEquals(tile2, hand.lastDrawn)
        assertEquals(2, hand.allTiles.size)

        // 模擬玩家捨棄剛摸到的牌
        val discarded = hand.discardById(id2)
        assertEquals(tile2, discarded)
        assertNull(hand.lastDrawn)
        assertEquals(1, hand.allTiles.size)
    }

    /**
     * 測試排序功能，確保使用 tiles.sortWith(compareBy(order) { it.tile }) 邏輯正確。
     */
    @Test
    fun `test sorting with different regional orders`() {
        // 白、中
        val white = IdentifiedTile(UUID.randomUUID(), Tile.Honor.White)
        val red = IdentifiedTile(UUID.randomUUID(), Tile.Honor.Red)

        // 初始順序：紅 -> 白
        val hand = Hand(mutableListOf(red))
        hand.lastDrawn = white

        // 測試日麻排序 (白 < 發 < 中)
        hand.organize(RiichiTileOrder)
        assertEquals(white, hand.standingTiles[0])
        assertEquals(red, hand.standingTiles[1])
        assertNull(hand.lastDrawn)

        // 測試台麻排序 (中 < 發 < 白)
        val handTaiwan = Hand(mutableListOf(red))
        handTaiwan.lastDrawn = white
        handTaiwan.organize(TaiwanTileOrder)
        assertEquals(red, handTaiwan.standingTiles[0])
        assertEquals(white, handTaiwan.standingTiles[1])
    }

    /**
     * 驗證 removeFromHand 內部邏輯是否能正確從立牌或摸牌中移除。
     */
    @Test
    fun `test discardById from standing tiles`() {
        val id1 = UUID.randomUUID()
        val tile1 = IdentifiedTile(id1, Tile.Numeric(Tile.Suit.Bamboo, 1))
        val id2 = UUID.randomUUID()
        val tile2 = IdentifiedTile(id2, Tile.Numeric(Tile.Suit.Bamboo, 2))

        val hand = Hand(mutableListOf(tile1, tile2))

        val discarded = hand.discardById(id1)
        assertEquals(tile1, discarded)
        assertEquals(1, hand.standingTiles.size)
        assertEquals(tile2, hand.standingTiles[0])
    }
}