package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame

/** [BuiltInDefaultGameConfigProvider] 的內建預設值測試。 */
class BuiltInDefaultGameConfigProviderTest {
    /** 驗證內建提供者維持四人日本麻將與標準流程設定。 */
    @Test
    fun `built-in provider creates the current riichi defaults`() {
        val config = BuiltInDefaultGameConfigProvider().create()

        assertIs<RiichiRuleConfig>(config.ruleConfig)
        assertEquals(4, config.ruleConfig.minPlayers)
        assertEquals(4, config.ruleConfig.maxPlayers)
        assertEquals(GameFlowConfig(), config.flowConfig)
    }

    /** 驗證每次建房會取得不同的完整設定實例。 */
    @Test
    fun `built-in provider creates an independent config for each room`() {
        val provider = BuiltInDefaultGameConfigProvider()

        val first = provider.create()
        val second = provider.create()

        assertEquals(first, second)
        assertNotSame(first, second)
        assertNotSame(first.ruleConfig, second.ruleConfig)
    }
}
