package com.doublemoon1119.mahjongcraft.domain.rules.taiwan

import com.doublemoon1119.mahjongcraft.domain.base.Tile
import com.doublemoon1119.mahjongcraft.domain.fakes.FakeTaiwanRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 針對 [TaiwanWallFactory] 進行的單元測試。
 *
 * 驗證台灣麻將牌山的組成是否符合預期，包含基礎 136 張牌以及可選的 8 張花牌。
 */
class TaiwanWallFactoryTest {

    /**
     * 驗證台麻牌山在啟用花牌時的總數與組成。
     *
     * 預期總數為 144 張，且應包含 8 張花牌。
     */
    @Test
    fun `test taiwan wall composition with flowers`() {
        val config = FakeTaiwanRuleConfig(useFlowerTiles = true)
        val factory = TaiwanWallFactory(config)
        val wall = factory.create()

        // 136 (基礎) + 8 (花牌) = 144
        assertEquals(144, wall.remainingCount)

        val allTiles = wall.getAllTiles()
        val flowerCount = allTiles.count { it.tile is Tile.Flower }
        assertEquals(8, flowerCount)
    }

    /**
     * 驗證台麻牌山在停用花牌時的總數與組成。
     *
     * 預期總數為 136 張，且不應包含任何花牌。
     */
    @Test
    fun `test taiwan wall composition without flowers`() {
        val config = FakeTaiwanRuleConfig(useFlowerTiles = false)
        val factory = TaiwanWallFactory(config)
        val wall = factory.create()

        // 僅 136 張基礎牌
        assertEquals(136, wall.remainingCount)
        assertTrue(wall.getAllTiles().none { it.tile is Tile.Flower })
    }
}