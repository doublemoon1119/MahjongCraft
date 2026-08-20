package com.doublemoon1119.mahjongcraft.logic.base

import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 針對 [HandSnapshot] 與 [Hand.toSnapshot] 進行單元測試。
 *
 * 驗證手牌快照的立牌與摸牌可見性控制邏輯。
 */
class HandSnapshotTest {

    /**
     * 驗證當 isVisible 為 true 時，快照應包含完整的立牌與摸牌資訊。
     */
    @Test
    fun `test toSnapshot with visible hand preserves all tiles`() {
        val tile1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val tile2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2))
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 3))

        val hand = Hand(mutableListOf(tile1, tile2), lastDrawn = lastDrawn)

        val snapshot = hand.toSnapshot(isVisible = true, revealsClosedKanTiles = true)

        assertEquals(3, snapshot.standingTiles.size)
        assertEquals(tile1.tile, snapshot.standingTiles[0].tile)
        assertEquals(tile2.tile, snapshot.standingTiles[1].tile)
        assertEquals(lastDrawn.tile, snapshot.standingTiles[2].tile)
        assertEquals(lastDrawn.id, snapshot.lastDrawn?.id)
        assertEquals(lastDrawn.tile, snapshot.lastDrawn?.tile)
    }

    /**
     * 驗證當 isVisible 為 false 時，快照中的牌張資訊應全部被隱藏，僅保留 ID。
     */
    @Test
    fun `test toSnapshot with hidden hand sets all tile info to null`() {
        val tile1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val tile2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2))
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 3))

        val hand = Hand(mutableListOf(tile1, tile2), lastDrawn = lastDrawn)

        val snapshot = hand.toSnapshot(isVisible = false, revealsClosedKanTiles = true)

        assertEquals(3, snapshot.standingTiles.size)
        assertEquals(tile1.id, snapshot.standingTiles[0].id)
        assertNull(snapshot.standingTiles[0].tile)
        assertEquals(tile2.id, snapshot.standingTiles[1].id)
        assertNull(snapshot.standingTiles[1].tile)
        assertEquals(lastDrawn.id, snapshot.standingTiles[2].id)
        assertNull(snapshot.standingTiles[2].tile)
        assertEquals(lastDrawn.id, snapshot.lastDrawn?.id)
        assertNull(snapshot.lastDrawn?.tile)
    }

    /**
     * 驗證當手牌沒有摸牌（lastDrawn 為 null）時，快照的 lastDrawn 也應為 null。
     */
    @Test
    fun `test toSnapshot with no lastDrawn returns null for lastDrawn`() {
        val tile1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val hand = Hand(mutableListOf(tile1))

        val snapshot = hand.toSnapshot(isVisible = true, revealsClosedKanTiles = true)

        assertEquals(1, snapshot.standingTiles.size)
        assertNull(snapshot.lastDrawn)
    }

    /**
     * 驗證空手牌的快照應產生空的立牌列表。
     */
    @Test
    fun `test toSnapshot with empty hand returns empty standing tiles`() {
        val hand = Hand()

        val snapshot = hand.toSnapshot(isVisible = true, revealsClosedKanTiles = true)

        assertEquals(0, snapshot.standingTiles.size)
        assertNull(snapshot.lastDrawn)
    }

    /**
     * 驗證非暗槓副露（吃／碰／明槓／加槓）本身就是公開宣告，永遠完整可見，不受 isVisible 或
     * revealsClosedKanTiles 影響。
     */
    @Test
    fun `test toSnapshot with pon meld is always visible regardless of flags`() {
        val calledTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val meldTiles = listOf(
            calledTile,
            FakeIdentifiedTileFactory.create(Tile.Honor.White),
            FakeIdentifiedTileFactory.create(Tile.Honor.White),
        )
        val hand = Hand(melds = listOf(Meld(MeldType.PON, meldTiles, calledTile, RelativeDirection.Left)))

        val snapshot = hand.toSnapshot(isVisible = false, revealsClosedKanTiles = false)

        assertEquals(1, snapshot.melds.size)
        assertEquals(MeldType.PON, snapshot.melds[0].type)
        snapshot.melds[0].tiles.forEach { assertEquals(Tile.Honor.White, it.tile) }
    }

    /**
     * 驗證暗槓在規則公開暗槓身份（如日本麻將）時，即使不是手牌本人視角也完整可見。
     */
    @Test
    fun `test toSnapshot with closed kan meld visible when rule reveals it`() {
        val meldTiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 9)) }
        val hand = Hand(melds = listOf(Meld(MeldType.CLOSED_KAN, meldTiles, sourceDirection = RelativeDirection.Self)))

        val snapshot = hand.toSnapshot(isVisible = false, revealsClosedKanTiles = true)

        assertEquals(MeldType.CLOSED_KAN, snapshot.melds[0].type)
        snapshot.melds[0].tiles.forEach { assertEquals(Tile.Numeric(Tile.Suit.Bamboo, 9), it.tile) }
    }

    /**
     * 驗證暗槓在規則不公開暗槓身份（如台灣麻將）且非手牌本人視角時，牌面應被隱藏，僅保留 ID。
     */
    @Test
    fun `test toSnapshot with closed kan meld hidden when rule does not reveal it and not owner`() {
        val meldTiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 9)) }
        val hand = Hand(melds = listOf(Meld(MeldType.CLOSED_KAN, meldTiles, sourceDirection = RelativeDirection.Self)))

        val snapshot = hand.toSnapshot(isVisible = false, revealsClosedKanTiles = false)

        assertEquals(MeldType.CLOSED_KAN, snapshot.melds[0].type)
        snapshot.melds[0].tiles.forEach { assertNull(it.tile) }
    }

    /**
     * 驗證暗槓即使規則不公開，手牌本人視角仍應完整可見自己的暗槓。
     */
    @Test
    fun `test toSnapshot with closed kan meld visible to owner regardless of rule`() {
        val meldTiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 9)) }
        val hand = Hand(melds = listOf(Meld(MeldType.CLOSED_KAN, meldTiles, sourceDirection = RelativeDirection.Self)))

        val snapshot = hand.toSnapshot(isVisible = true, revealsClosedKanTiles = false)

        assertEquals(MeldType.CLOSED_KAN, snapshot.melds[0].type)
        snapshot.melds[0].tiles.forEach { assertEquals(Tile.Numeric(Tile.Suit.Bamboo, 9), it.tile) }
    }
}
