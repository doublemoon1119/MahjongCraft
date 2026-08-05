package com.doublemoon1119.mahjongcraft.logic.base

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiTileOrder
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanTileOrder
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.uuid.Uuid
import kotlin.test.*

/**
 * 針對 [Hand] 的基礎功能進行單元測試。
 *
 * [Hand] 為不可變值物件，所有操作皆回傳反映變更後狀態的新實例，
 * 因此測試中以 `hand = hand.xxx(...)` 的重新賦值方式驗證各項操作。
 *
 * 驗證包含摸牌、捨牌（含摸切判定與手牌位移）、以及不同規則下的排序邏輯。
 */
class HandTest {

    /**
     * 驗證 addTile 方法是否能正確回傳將牌加入立牌清單後的新手牌。
     */
    @Test
    fun `test addTile`() {
        // Arrange
        var hand = Hand()
        val tile1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val tile2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5))

        // Act
        hand = hand.addTile(tile1)
        hand = hand.addTile(tile2)

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
        val id1 = Uuid.random()
        val tile1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1), id = id1)
        var hand = Hand(listOf(tile1))

        val id2 = Uuid.random()
        val tile2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2), id = id2)

        // 模擬摸牌
        hand = hand.copy(lastDrawn = tile2)
        assertEquals(tile2, hand.lastDrawn)
        assertEquals(2, hand.allTiles.size)

        // 模擬玩家捨棄剛摸到的牌（摸切）
        val result = hand.discardById(id2)

        assertTrue(result != null, "Discard result should not be null.")
        assertEquals(tile2, result.tile, "The discarded tile should be the one just drawn.")
        assertTrue(result.isDiscardedFromDraw, "This action should be identified as a draw-cut.")

        hand = result.hand
        assertNull(hand.lastDrawn, "lastDrawn should be cleared after discard.")
        assertEquals(1, hand.allTiles.size)
    }

    /**
     * 驗證當玩家打出手牌而非剛摸到的牌時，lastDrawn 應自動併入立牌中（非摸切）。
     */
    @Test
    fun `test non tsumogiri discard moves lastDrawn to standing tiles`() {
        val idInHand = Uuid.random()
        val tileInHand = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5), id = idInHand)

        val idLastDrawn = Uuid.random()
        val tileLastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 9), id = idLastDrawn)

        val hand = Hand(tiles = listOf(tileInHand), lastDrawn = tileLastDrawn)

        // 執行捨牌：打出手中的 5 萬，而非剛摸到的 9 萬
        val result = hand.discardById(idInHand)

        assertTrue(result != null)
        assertEquals(tileInHand, result.tile)
        assertFalse(result.isDiscardedFromDraw, "Discarding a tile from standing tiles should not be a draw-cut.")

        // 驗證原本摸到的 9 萬是否已經自動併入手牌清單
        val updatedHand = result.hand
        assertNull(updatedHand.lastDrawn, "lastDrawn should be cleared because it was moved to standing tiles.")
        assertTrue(
            updatedHand.standingTiles.contains(tileLastDrawn),
            "The last drawn tile should now be in the standing tiles list."
        )
        assertEquals(1, updatedHand.standingTiles.size)
    }

    /**
     * 測試排序功能，確保 organize() 回傳的新手牌依照指定的 [TileOrder] 正確排序。
     */
    @Test
    fun `test sorting with different regional orders`() {
        val white = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val red = FakeIdentifiedTileFactory.create(Tile.Honor.Red)

        // 測試日麻排序 (白 < 中)
        val hand = Hand(tiles = listOf(red), lastDrawn = white).organize(RiichiTileOrder)
        assertEquals(white, hand.standingTiles[0])
        assertEquals(red, hand.standingTiles[1])
        assertNull(hand.lastDrawn)

        // 測試台麻排序 (中 < 白)
        val handTaiwan = Hand(tiles = listOf(red), lastDrawn = white).organize(TaiwanTileOrder)
        assertEquals(red, handTaiwan.standingTiles[0])
        assertEquals(white, handTaiwan.standingTiles[1])
    }

    /**
     * 驗證 discardById 從立牌中移除牌的基礎正確性。
     */
    @Test
    fun `test discardById from standing tiles`() {
        val id1 = Uuid.random()
        val tile1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 1), id = id1)
        val id2 = Uuid.random()
        val tile2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 2), id = id2)

        val hand = Hand(listOf(tile1, tile2))

        val result = hand.discardById(id1)
        assertEquals(tile1, result?.tile)

        val updatedHand = result!!.hand
        assertEquals(1, updatedHand.standingTiles.size)
        assertEquals(tile2, updatedHand.standingTiles[0])
    }
}
