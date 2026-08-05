package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 針對 [TileWall] 進行單元測試。
 *
 * [TileWall] 為不可變值物件，[TileWall.draw]/[TileWall.drawLast] 皆透過 [TileWall.DrawResult]
 * 回傳摸到的牌與新的牌山狀態，因此測試以 `wall = result.wall` 的重新賦值方式驗證。
 */
class TileWallTest {

    /**
     * 驗證從牌山前方摸牌的邏輯，是否正確減少牌山數量。
     */
    @Test
    fun `test drawing from wall`() {
        val tiles = listOf(
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)),
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2))
        )
        var wall = TileWall(tiles)

        assertEquals(2, wall.remainingCount)

        val firstDraw = wall.draw()
        assertNotNull(firstDraw.tile)
        wall = firstDraw.wall
        assertEquals(1, wall.remainingCount)

        val secondDraw = wall.draw()
        assertNotNull(secondDraw.tile)
        wall = secondDraw.wall
        assertEquals(0, wall.remainingCount)

        // 牌山空了應返回 null，且牌山狀態不變
        val thirdDraw = wall.draw()
        assertNull(thirdDraw.tile)
        assertEquals(0, thirdDraw.wall.remainingCount)
    }

    /**
     * 驗證從牌山後方摸牌的邏輯，是否正確減少牌山數量。
     */
    @Test
    fun `test drawing last from wall`() {
        val tiles = listOf(
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1)),
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2))
        )
        var wall = TileWall(tiles)

        assertEquals(2, wall.remainingCount)

        val firstDraw = wall.drawLast()
        assertNotNull(firstDraw.tile)
        wall = firstDraw.wall
        assertEquals(1, wall.remainingCount)

        val secondDraw = wall.draw()
        assertNotNull(secondDraw.tile)
        wall = secondDraw.wall
        assertEquals(0, wall.remainingCount)

        // 牌山空了應返回 null
        assertNull(wall.draw().tile)
    }

    /**
     * 驗證從牌山讀取特定位置的牌的邏輯。
     */
    @Test
    fun `test peeking from wall`() {
        val tile1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))
        val tile2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 2))
        val wall = TileWall(listOf(tile1, tile2))

        assertEquals(2, wall.remainingCount)

        val firstTile = wall.peekAt(0)
        assertNotNull(firstTile)
        assertEquals(firstTile, tile1)

        val secondTile = wall.peekAt(1)
        assertNotNull(secondTile)
        assertEquals(secondTile, tile2)
    }

    /**
     * 驗證獲取所有牌的列表後，摸牌不會影響原本已取得的列表或原本的牌山實例。
     */
    @Test
    fun `test getAllTiles returns immutable list copy`() {
        val wall = TileWall(listOf(FakeIdentifiedTileFactory.create(Tile.Honor.West)))

        val allTiles = wall.getAllTiles()
        assertEquals(1, allTiles.size)

        // 從新回傳的牌山實例摸牌，不應影響原本的 wall 或先前取得的 allTiles
        val afterDraw = wall.draw().wall
        assertEquals(1, allTiles.size)
        assertEquals(1, wall.remainingCount, "The original wall instance should remain unchanged.")
        assertEquals(0, afterDraw.remainingCount)
    }
}
