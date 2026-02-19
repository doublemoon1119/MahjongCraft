package com.doublemoon1119.mahjongcraft.model.riichi

import com.doublemoon1119.mahjongcraft.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 針對 [RiichiWallFactory] 進行單元測試。
 */
class RiichiWallFactoryTest {

    /**
     * 驗證日麻牌山總數與赤寶牌配置。
     */
    @Test
    fun `test riichi wall composition`() {
        val factory = RiichiWallFactory(redFiveCount = 1)
        val wall = factory.create()

        assertEquals(136, wall.remainingCount)

        val allTiles = wall.getAllTiles()

        // 驗證赤牌總數
        val redCount = allTiles.count { (it.tile as? Tile.Numeric)?.isRed == true }
        assertEquals(3, redCount)

        // 驗證 UUID 唯一性 (不應有重複的 ID)
        val uniqueIds = allTiles.map { it.id }.toSet()
        assertEquals(136, uniqueIds.size)
    }
}