package com.doublemoon1119.mahjongcraft.model.taiwan.factory

import com.doublemoon1119.mahjongcraft.model.base.Tile
import com.doublemoon1119.mahjongcraft.model.config.GameLength
import com.doublemoon1119.mahjongcraft.model.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.model.taiwan.TaiwanScoreConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 針對 [TaiwanWallFactory] 進行單元測試。
 */
class TaiwanWallFactoryTest {

    /**
     * 模擬台麻配置實作類別。
     */
    private class TaiwanMock(override val useFlowerTiles: Boolean) : TaiwanRuleConfig {
        override val initialHandSize = 16
        override val tileSet = emptyList<Tile>()
        override val deadTileCount = 8
        override val minimumWinConstraint = 0
        override val scoreConfig = TaiwanScoreConfig(30, 10)
        override val gameLength = object : GameLength {
            override val totalRounds = 16
            override val name = "ONE_SHOE"
        }
    }

    /**
     * 驗證台麻牌山在啟用花牌時的總數與組成。
     */
    @Test
    fun `test taiwan wall composition with flowers`() {
        val config = TaiwanMock(useFlowerTiles = true)
        val factory = TaiwanWallFactory(config)
        val wall = factory.create()

        // 136 (基礎) + 8 (花牌) = 144
        assertEquals(144, wall.remainingCount)

        val allTiles = wall.getAllTiles()
        val flowerCount = allTiles.count { it.tile is Tile.Flower }
        assertEquals(8, flowerCount)
    }

    /**
     * 驗證台麻牌山在停用花牌時的總數。
     */
    @Test
    fun `test taiwan wall composition without flowers`() {
        val config = TaiwanMock(useFlowerTiles = false)
        val factory = TaiwanWallFactory(config)
        val wall = factory.create()

        assertEquals(136, wall.remainingCount)

        val flowerCount = wall.getAllTiles().count { it.tile is Tile.Flower }
        assertEquals(0, flowerCount)
    }
}