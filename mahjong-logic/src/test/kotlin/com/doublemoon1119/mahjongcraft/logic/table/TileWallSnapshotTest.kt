package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 針對 [TileWallSnapshot] 與 [TileWall.toSnapshot] 進行單元測試。
 *
 * 驗證牌山快照依據 visibleTileIds 控制可見牌張的邏輯。
 */
class TileWallSnapshotTest {

    /**
     * 驗證當 visibleTileIds 包含某張牌的 ID 時，該牌在快照中應可見。
     */
    @Test
    fun `test toSnapshot reveals tiles whose ids are in visibleTileIds`() {
        val tile1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val tile2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2))
        val tile3 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 3))
        val wall = TileWall(mutableListOf(tile1, tile2, tile3))

        val visibleTileIds = setOf(tile1.id, tile3.id)
        val snapshot = wall.toSnapshot(visibleTileIds)

        assertEquals(3, snapshot.tiles.size)
        assertEquals(tile1.tile, snapshot.tiles[0].tile)
        assertNull(snapshot.tiles[1].tile, "Tile2 should be hidden.")
        assertEquals(tile3.tile, snapshot.tiles[2].tile)
    }

    /**
     * 驗證當 visibleTileIds 為空集合時，所有牌在快照中都應被隱藏。
     */
    @Test
    fun `test toSnapshot with empty visibleTileIds hides all tiles`() {
        val tile1 = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val tile2 = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val wall = TileWall(mutableListOf(tile1, tile2))

        val snapshot = wall.toSnapshot(emptySet())

        assertEquals(2, snapshot.tiles.size)
        assertEquals(tile1.id, snapshot.tiles[0].id)
        assertNull(snapshot.tiles[0].tile)
        assertEquals(tile2.id, snapshot.tiles[1].id)
        assertNull(snapshot.tiles[1].tile)
    }

    /**
     * 驗證當 visibleTileIds 包含所有牌的 ID 時，所有牌在快照中都應可見。
     */
    @Test
    fun `test toSnapshot reveals all tiles when all ids are visible`() {
        val tile1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 5))
        val tile2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, 6))
        val wall = TileWall(mutableListOf(tile1, tile2))

        val visibleTileIds = setOf(tile1.id, tile2.id)
        val snapshot = wall.toSnapshot(visibleTileIds)

        assertEquals(2, snapshot.tiles.size)
        assertEquals(tile1.tile, snapshot.tiles[0].tile)
        assertEquals(tile2.tile, snapshot.tiles[1].tile)
    }

    /**
     * 驗證空牌山的快照應產生空的 tiles 列表。
     */
    @Test
    fun `test toSnapshot with empty wall returns empty tiles list`() {
        val wall = TileWall(emptyList())

        val snapshot = wall.toSnapshot(emptySet())

        assertEquals(0, snapshot.tiles.size)
    }

    /**
     * 驗證快照中的牌張順序應與牌山中的原始順序一致。
     */
    @Test
    fun `test toSnapshot preserves original tile order`() {
        val tiles = List(5) { i ->
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, i + 1))
        }
        val wall = TileWall(tiles.toMutableList())

        val visibleTileIds = tiles.map { it.id }.toSet()
        val snapshot = wall.toSnapshot(visibleTileIds)

        for (i in tiles.indices) {
            assertEquals(tiles[i].tile, snapshot.tiles[i].tile, "Tile order at index $i should be preserved.")
        }
    }
}
