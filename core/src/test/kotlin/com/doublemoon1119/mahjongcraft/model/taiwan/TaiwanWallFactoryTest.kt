package com.doublemoon1119.mahjongcraft.model.taiwan

import com.doublemoon1119.mahjongcraft.model.base.Tile
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 針對 [TaiwanWallFactory] 進行單元測試。
 */
class TaiwanWallFactoryTest {

    /**
     * 驗證台麻牌山總數與花牌配置。
     */
    @Test
    fun `test taiwan wall composition`() {
        val factory = TaiwanWallFactory()
        val wall = factory.create()

        assertEquals(144, wall.remainingCount)

        val allTiles = wall.getAllTiles()

        // 驗證花牌總數
        val flowerCount = allTiles.count { it.tile is Tile.Flower }
        assertEquals(8, flowerCount)

        // 驗證 UUID 唯一性
        val uniqueIds = allTiles.map { it.id }.toSet()
        assertEquals(144, uniqueIds.size)
    }
}