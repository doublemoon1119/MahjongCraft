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
        // 設定每種花色 1 張赤 5
        val factory = RiichiWallFactory(redFiveCount = 1)
        val wall = factory.create()

        // 總數應為 136
        assertEquals(136, wall.remainingCount)

        // 驗證赤牌總數應為 3 (紅5萬, 紅5筒, 紅5條)
        val allTiles = wall.getAllTiles()
        val redCount = allTiles.filterIsInstance<Tile.Numeric>().count { it.isRed }
        assertEquals(3, redCount)

        // 驗證不含花牌
        val flowerCount = allTiles.count { it is Tile.Flower }
        assertEquals(0, flowerCount)
    }
}