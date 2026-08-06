package com.doublemoon1119.mahjongcraft.logic.base

import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * 針對 [IdentifiedTileSnapshot] 與 [IdentifiedTile.toSnapshot] 進行單元測試。
 *
 * 驗證快照的可見性控制邏輯與資料封裝正確性。
 */
class IdentifiedTileSnapshotTest {

    /**
     * 驗證當 isVisible 為 true 時，快照應保留完整的牌張資訊。
     */
    @Test
    fun `test toSnapshot with visible tile preserves tile info`() {
        val tile = Tile.Numeric(Tile.Suit.Dot, 5)
        val id = Uuid.random()
        val identifiedTile = FakeIdentifiedTileFactory.create(tile = tile, id = id)

        val snapshot = identifiedTile.toSnapshot(isVisible = true)

        assertEquals(id, snapshot.id)
        assertEquals(tile, snapshot.tile)
    }

    /**
     * 驗證當 isVisible 為 false 時，快照的牌張資訊應被隱藏（為 null）。
     */
    @Test
    fun `test toSnapshot with hidden tile sets tile to null`() {
        val tile = Tile.Honor.Red
        val id = Uuid.random()
        val identifiedTile = FakeIdentifiedTileFactory.create(tile = tile, id = id)

        val snapshot = identifiedTile.toSnapshot(isVisible = false)

        assertEquals(id, snapshot.id)
        assertNull(snapshot.tile, "Tile info should be null when visibility is false.")
    }

    /**
     * 驗證快照的 ID 無論可見性為何都應始終保留。
     */
    @Test
    fun `test snapshot always retains id regardless of visibility`() {
        val id = Uuid.random()
        val identifiedTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 3), id = id)

        val visibleSnapshot = identifiedTile.toSnapshot(isVisible = true)
        val hiddenSnapshot = identifiedTile.toSnapshot(isVisible = false)

        assertEquals(id, visibleSnapshot.id)
        assertEquals(id, hiddenSnapshot.id)
    }
}
