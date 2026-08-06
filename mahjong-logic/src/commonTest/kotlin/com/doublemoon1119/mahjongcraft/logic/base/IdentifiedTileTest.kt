package com.doublemoon1119.mahjongcraft.logic.base

import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.uuid.Uuid

/**
 * 針對 [IdentifiedTile] 進行單元測試。
 */
class IdentifiedTileTest {

    /**
     * 驗證當兩張牌的種類相同但 ID 不同時，它們不應被判定為相等。
     */
    @Test
    fun `test identity equality with same tile type`() {
        val tileType = Tile.Numeric(Tile.Suit.Dot, 1)
        val id1 = Uuid.random()
        val id2 = Uuid.random()

        val identifiedTile1 = FakeIdentifiedTileFactory.create(id = id1, tile = tileType)
        val identifiedTile2 = FakeIdentifiedTileFactory.create(id = id2, tile = tileType)

        // ID 不同，對象應不相等
        assertNotEquals(identifiedTile1, identifiedTile2)
        assertEquals(identifiedTile1.tile, identifiedTile2.tile)
    }

    /**
     * 驗證 Data Class 的複製功能是否能正確運作。
     */
    @Test
    fun `test copy with new id`() {
        val original = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val newId = Uuid.random()
        val copied = original.copy(id = newId)

        assertEquals(newId, copied.id)
        assertEquals(original.tile, copied.tile)
    }
}
