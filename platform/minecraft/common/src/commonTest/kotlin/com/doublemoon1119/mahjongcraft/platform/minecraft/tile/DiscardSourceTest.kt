package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證準星指著的牌查得出是誰打出的，以及打出者相對觀看者的位置。 */
class DiscardSourceTest {
    private val inRiver = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
    private val called = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2))
    private val handTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 3))
    private val east = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST, hand = Hand(tiles = listOf(handTile)))
    private val south = FakeMahjongPlayerFactory.create(
        initialSeat = Wind.SOUTH,
        discardPile = FakeDiscardPile().discardTile(inRiver).discardTile(called).takeLast(),
    )
    private val west = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST)
    private val north = FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH)
    private val snapshot = FakeTableStateFactory.create(players = listOf(east, south, west, north)).toSnapshot(emptySet())

    /** 還在牌河的牌與被鳴走、移到副露的牌都查得到打出者。 */
    @Test
    fun `discards in the river and called tiles both find their discarder`() {
        assertEquals(south.id, DiscardSource.of(snapshot, inRiver.id, viewerId = null)?.discarderId)
        assertEquals(south.id, DiscardSource.of(snapshot, called.id, viewerId = null)?.discarderId)
    }

    /** 沒打出過的牌（例如手牌或加槓補上的牌）查不到打出者。 */
    @Test
    fun `tiles that were never discarded have no discarder`() {
        assertNull(DiscardSource.of(snapshot, handTile.id, viewerId = east.id))
        assertNull(DiscardSource.of(snapshot, Uuid.random(), viewerId = east.id))
    }

    /** 入座時依座位順序得出上家、對家、下家或自己；旁觀時沒有相對位置。 */
    @Test
    fun `relation follows the seat order and is absent for spectators`() {
        assertEquals(RelativeDirection.Right, DiscardSource.of(snapshot, inRiver.id, east.id)?.relation)
        assertEquals(RelativeDirection.Self, DiscardSource.of(snapshot, inRiver.id, south.id)?.relation)
        assertEquals(RelativeDirection.Left, DiscardSource.of(snapshot, inRiver.id, west.id)?.relation)
        assertEquals(RelativeDirection.Across, DiscardSource.of(snapshot, inRiver.id, north.id)?.relation)
        assertNull(DiscardSource.of(snapshot, inRiver.id, Uuid.random())?.relation)
        assertEquals(south.id, DiscardSource.of(snapshot, inRiver.id, Uuid.random())?.discarderId)
    }
}
