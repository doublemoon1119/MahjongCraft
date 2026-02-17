package com.doublemoon1119.mahjongcraft.model.taiwan

import com.doublemoon1119.mahjongcraft.model.Tile
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

        // 總數應為 144
        assertEquals(144, wall.remainingCount)

        // 驗證花牌總數應為 8
        val allTiles = wall.getAllTiles()
        val flowerCount = allTiles.count { it is Tile.Flower }
        assertEquals(8, flowerCount)

        // 驗證台麻不應有赤牌
        val redCount = allTiles.filterIsInstance<Tile.Numeric>().count { it.isRed }
        assertEquals(0, redCount)
    }
}