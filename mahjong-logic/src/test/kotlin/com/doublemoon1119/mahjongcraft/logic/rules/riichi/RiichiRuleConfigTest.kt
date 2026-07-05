package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 驗證 [RiichiRuleConfig] 的設定屬性。
 */
class RiichiRuleConfigTest {

    /**
     * 測試預設建構的 [RiichiRuleConfig] 是否具有正確的初始值。
     */
    @Test
    fun `test default config values`() {
        val config = RiichiRuleConfig()

        assertEquals(13, config.initialHandSize)
        assertEquals(14, config.deadTileCount)
        assertEquals(4, config.minPlayers)
        assertEquals(4, config.maxPlayers)
        assertEquals(1, config.minimumWinConstraint)
        assertEquals(3, config.redDoraCount)
        assertEquals(true, config.allowOpenTanyao)
        assertEquals(false, config.useLocalYaku)
    }

    /**
     * 測試自定義赤牌數量的設定。
     */
    @Test
    fun `test custom red dora count`() {
        val config = RiichiRuleConfig(redDoraCount = 4)

        assertEquals(4, config.redDoraCount)
    }
}
