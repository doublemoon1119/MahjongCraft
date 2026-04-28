package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 針對日本麻將規則配置 [RiichiRuleConfig] 進行測試。
 */
class RiichiRuleConfigTest {
    /**
     * 測試日本麻將特有屬性、積分規則與胡牌限制的存取與正確性。
     */
    @Test
    fun `test riichi specific configuration`() {
        // 初始化模擬配置
        val config = RiichiRuleConfig(
            redDoraCount = 4,
            allowOpenTanyao = true,
            useLocalYaku = true
        )

        // 驗證繼承自基礎介面的通用屬性
        assertEquals(13, config.initialHandSize)
        assertEquals(1, config.minimumWinConstraint, "Riichi should have 1-han constraint")

        // 驗證日麻特有的規則開關
        assertEquals(4, config.redDoraCount)
        assertTrue(config.allowOpenTanyao, "Should support open tanyao configuration")
        assertTrue(config.useLocalYaku, "Should support local yaku configuration")

        // 驗證積分配置（包含新增的 1 位必要點數）
        assertEquals(25000, config.scoreConfig.initialScore)
        assertEquals(0, config.scoreConfig.bustThreshold)
        assertEquals(30000, config.scoreConfig.minPointsToWin, "Default min points to win should be 30000")

        // 驗證遊戲長度配置
        assertEquals(RiichiGameLength.OneGame, config.gameLength)
    }
}