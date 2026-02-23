package com.doublemoon1119.mahjongcraft.model.taiwan

import com.doublemoon1119.mahjongcraft.model.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 針對台灣麻將規則配置 [TaiwanRuleConfig] 進行測試。
 */
class TaiwanRuleConfigTest {

    /**
     * 模擬台灣麻將配置實作類別。
     *
     * @property useFlowerTiles 指定是否使用花牌。
     * @property scoreConfig 積分配置實作，預設底分為 30，台分為 10。
     */
    private class TaiwanMock(
        override val useFlowerTiles: Boolean = true,
        override val scoreConfig: TaiwanScoreConfig = TaiwanScoreConfig(baseScore = 30, pointPerTai = 10)
    ) : TaiwanRuleConfig {
        /** 固定台麻標準初始手牌張數 16。 */
        override val initialHandSize = 16
        /** 測試用空牌組。 */
        override val tileSet = emptyList<Tile>()
        /** 固定台麻王牌/保留牌張數 8。 */
        override val deadTileCount = 8
    }

    /**
     * 測試台灣麻將特有屬性（如花牌開關）與底台積分配置的存取與正確性。
     */
    @Test
    fun `test taiwan specific configuration`() {
        // 初始化啟用花牌且底 30 台 10 的模擬配置
        val config = TaiwanMock(useFlowerTiles = true)

        // 驗證基礎屬性
        assertEquals(16, config.initialHandSize)

        // 驗證台麻特有的花牌啟用法則
        assertTrue(config.useFlowerTiles, "Taiwan rules should allow toggling flower tiles")

        // 驗證台麻特有的積分配置（底與台）
        assertEquals(30, config.scoreConfig.baseScore, "Base score should be 30")
        assertEquals(10, config.scoreConfig.pointPerTai, "Point per Tai should be 10")
    }
}