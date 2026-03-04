package com.doublemoon1119.mahjongcraft.domain.base

import com.doublemoon1119.mahjongcraft.domain.riichi.RiichiTileOrder
import com.doublemoon1119.mahjongcraft.domain.taiwan.TaiwanTileOrder
import java.util.*
import kotlin.test.*

/**
 * 針對 [Hand] 的基礎功能進行單元測試。
 *
 * 驗證包含摸牌、捨牌（含摸切判定與手牌位移）、以及不同規則下的排序邏輯。
 */
class HandTest {

    /**
     * 驗證 addTile 方法是否能正確將牌加入立牌清單中。
     */
    @Test
    fun `test addTile`() {
        // Arrange
        val hand = Hand()
        val tile1 = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 1))
        val tile2 = IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Bamboo, 5))

        // Act
        hand.addTile(tile1)
        hand.addTile(tile2)

        // Assert
        assertEquals(2, hand.standingTiles.size, "Hand should contain exactly 2 standing tiles.")
        assertTrue(hand.standingTiles.contains(tile1), "Hand should contain the first added tile.")
        assertTrue(hand.standingTiles.contains(tile2), "Hand should contain the second added tile.")
    }

    /**
     * 測試摸牌與透過 UUID 捨牌的邏輯，並驗證摸切 (Tsumogiri) 判定。
     */
    @Test
    fun `test drawing and discarding by id with tsumogiri check`() {
        val id1 = UUID.randomUUID()
        val tile1 = IdentifiedTile(id1, Tile.Numeric(Tile.Suit.Dot, 1))
        val hand = Hand(mutableListOf(tile1))

        val id2 = UUID.randomUUID()
        val tile2 = IdentifiedTile(id2, Tile.Numeric(Tile.Suit.Dot, 2))

        // 模擬摸牌
        hand.lastDrawn = tile2
        assertEquals(tile2, hand.lastDrawn)
        assertEquals(2, hand.allTiles.size)

        // 模擬玩家捨棄剛摸到的牌（摸切）
        val result = hand.discardById(id2)

        assertTrue(result != null, "Discard result should not be null.")
        assertEquals(tile2, result.tile, "The discarded tile should be the one just drawn.")
        assertTrue(result.isDiscardedFromDraw, "This action should be identified as a draw-cut.")
        assertNull(hand.lastDrawn, "lastDrawn should be cleared after discard.")
        assertEquals(1, hand.allTiles.size)
    }

    /**
     * 驗證當玩家打出手牌而非剛摸到的牌時，lastDrawn 應自動併入立牌中（非摸切）。
     */
    @Test
    fun `test non tsumogiri discard moves lastDrawn to standing tiles`() {
        val idInHand = UUID.randomUUID()
        val tileInHand = IdentifiedTile(idInHand, Tile.Numeric(Tile.Suit.Character, 5))

        val idLastDrawn = UUID.randomUUID()
        val tileLastDrawn = IdentifiedTile(idLastDrawn, Tile.Numeric(Tile.Suit.Character, 9))

        val hand = Hand(mutableListOf(tileInHand))
        hand.lastDrawn = tileLastDrawn

        // 執行捨牌：打出手中的 5 萬，而非剛摸到的 9 萬
        val result = hand.discardById(idInHand)

        assertTrue(result != null)
        assertEquals(tileInHand, result.tile)
        assertFalse(result.isDiscardedFromDraw, "Discarding a tile from standing tiles should not be a draw-cut.")

        // 驗證原本摸到的 9 萬是否已經自動併入手牌清單
        assertNull(hand.lastDrawn, "lastDrawn should be cleared because it was moved to standing tiles.")
        assertTrue(
            hand.standingTiles.contains(tileLastDrawn),
            "The last drawn tile should now be in the standing tiles list."
        )
        assertEquals(1, hand.standingTiles.size)
    }

    /**
     * 測試排序功能，確保使用 tiles.sortWith(compareBy(order) { it.tile }) 邏輯正確。
     */
    @Test
    fun `test sorting with different regional orders`() {
        val white = IdentifiedTile(UUID.randomUUID(), Tile.Honor.White)
        val red = IdentifiedTile(UUID.randomUUID(), Tile.Honor.Red)

        val hand = Hand(mutableListOf(red))
        hand.lastDrawn = white

        // 測試日麻排序 (白 < 中)
        hand.organize(RiichiTileOrder)
        assertEquals(white, hand.standingTiles[0])
        assertEquals(red, hand.standingTiles[1])
        assertNull(hand.lastDrawn)

        // 測試台麻排序 (中 < 白)
        val handTaiwan = Hand(mutableListOf(red))
        handTaiwan.lastDrawn = white
        handTaiwan.organize(TaiwanTileOrder)
        assertEquals(red, handTaiwan.standingTiles[0])
        assertEquals(white, handTaiwan.standingTiles[1])
    }

    /**
     * 驗證 discardById 從立牌中移除牌的基礎正確性。
     */
    @Test
    fun `test discardById from standing tiles`() {
        val id1 = UUID.randomUUID()
        val tile1 = IdentifiedTile(id1, Tile.Numeric(Tile.Suit.Bamboo, 1))
        val id2 = UUID.randomUUID()
        val tile2 = IdentifiedTile(id2, Tile.Numeric(Tile.Suit.Bamboo, 2))

        val hand = Hand(mutableListOf(tile1, tile2))

        val result = hand.discardById(id1)
        assertEquals(tile1, result?.tile)
        assertEquals(1, hand.standingTiles.size)
        assertEquals(tile2, hand.standingTiles[0])
    }
}