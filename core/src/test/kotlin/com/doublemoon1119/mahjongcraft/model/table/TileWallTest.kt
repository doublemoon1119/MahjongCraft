package com.doublemoon1119.mahjongcraft.model.table

import com.doublemoon1119.mahjongcraft.model.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.model.base.Tile
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 針對 [TileWall] 進行單元測試。
 */
class TileWallTest {

    /**
     * 驗證摸牌邏輯是否正確減少牌山數量。
     */
    @Test
    fun `test drawing from wall`() {
        val tiles = mutableListOf(
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 1)),
            IdentifiedTile(UUID.randomUUID(), Tile.Numeric(Tile.Suit.Dot, 2))
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
     * 驗證獲取所有牌的列表是否為唯讀複製品。
     */
    @Test
    fun `test getAllTiles returns immutable list copy`() {
        val tiles = mutableListOf(
            IdentifiedTile(UUID.randomUUID(), Tile.Honor.West)
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