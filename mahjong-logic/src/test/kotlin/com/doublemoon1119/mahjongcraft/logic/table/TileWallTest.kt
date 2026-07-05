package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 針對 [TileWall] 進行單元測試。
 */
class TileWallTest {

    /**
     * 驗證從牌山前方摸牌的邏輯，是否正確減少牌山數量。
     */
    @Test
    fun `test drawing from wall`() {
        val tiles = mutableListOf(
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)),
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2))
        )
        val wall = TileWall(tiles)

        assertEquals(2, wall.remainingCount)

        val firstDraw = wall.draw()
        assertNotNull(firstDraw)
        assertEquals(1, wall.remainingCount)

        val secondDraw = wall.draw()
        assertNotNull(secondDraw)
        assertEquals(0, wall.remainingCount)

        // 牌山空了應返回 null
        assertNull(wall.draw())
    }

    /**
     * 驗證從牌山後方摸牌的邏輯，是否正確減少牌山數量。
     */
    @Test
    fun `test drawing last from wall`() {
        val tiles = mutableListOf(
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)),
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2))
        )
        val wall = TileWall(tiles)

        assertEquals(2, wall.remainingCount)

        val firstDraw = wall.drawLast()
        assertNotNull(firstDraw)
        assertEquals(1, wall.remainingCount)

        val secondDraw = wall.draw()
        assertNotNull(secondDraw)
        assertEquals(0, wall.remainingCount)

        // 牌山空了應返回 null
        assertNull(wall.draw())
    }

    /**
     * 驗證從牌山讀取特定位置的牌的邏輯。
     */
    @Test
    fun `test peeking from wall`() {
        val tile1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val tile2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2))
        val tiles = mutableListOf(tile1, tile2)
        val wall = TileWall(tiles)

        assertEquals(2, wall.remainingCount)

        val firstTile = wall.peekAt(0)
        assertNotNull(firstTile)
        assertEquals(firstTile, tile1)

        val secondTile = wall.peekAt(1)
        assertNotNull(secondTile)
        assertEquals(secondTile, tile2)
    }

    /**
     * 驗證獲取所有牌的列表是否為唯讀複製品。
     */
    @Test
    fun `test getAllTiles returns immutable list copy`() {
        val tiles = mutableListOf(
            FakeIdentifiedTileFactory.create(Tile.Honor.West)
        )
        val wall = TileWall(tiles)

        val allTiles = wall.getAllTiles()
        assertEquals(1, allTiles.size)

        // 摸牌後，先前取得的 list 長度不應改變
        wall.draw()
        assertEquals(1, allTiles.size)
        assertEquals(0, wall.remainingCount)
    }
}