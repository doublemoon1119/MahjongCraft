package com.doublemoon1119.mahjongcraft.domain.rules.taiwan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 針對台灣麻將規則配置 [TaiwanRuleConfig] 進行測試。
 */
class TaiwanRuleConfigTest {
    /**
     * 測試台灣麻將特有屬性（如花牌開關）、底台積分配置與對局長度的存取與正確性。
     */
    @Test
    fun `test taiwan specific configuration`() {
        // 初始化模擬配置
        val config = TaiwanRuleConfig(useFlowerTiles = true)

        // 驗證基礎屬性
        assertEquals(16, config.initialHandSize)
        assertEquals(0, config.minimumWinConstraint, "Taiwan mahjong typically has no minimum Tai constraint")

        // 驗證台麻特有的花牌啟用法則
        assertTrue(config.useFlowerTiles, "Taiwan rules should allow toggling flower tiles")

        // 驗證台麻特有的積分配置（底與台）
        assertEquals(30, config.scoreConfig.baseScore, "Base score should be 30")
        assertEquals(10, config.scoreConfig.pointPerTai, "Point per Tai should be 10")
        assertNull(config.scoreConfig.bustThreshold, "Taiwan mahjong should not have a bust threshold by default")

        // 驗證遊戲長度配置
        assertEquals(TaiwanGameLength.OneGame, config.gameLength)
    }
}