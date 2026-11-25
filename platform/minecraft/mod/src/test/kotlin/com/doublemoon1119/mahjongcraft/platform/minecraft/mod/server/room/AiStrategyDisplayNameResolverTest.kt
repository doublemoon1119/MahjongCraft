package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證 AI 策略名稱的文字解析。 */
class AiStrategyDisplayNameResolverTest {
    /** 已登記的策略使用登記的翻譯鍵，未登記時顯示原始 key。 */
    @Test
    fun `registered strategies use their translation key and unknown ones show the raw key`() {
        val registry = AiStrategyDisplayNameRegistryImpl()
        registry.register(AiStrategyDisplayNameDefinition("example:strategy", "example.strategy.name"))

        assertEquals("example.strategy.name", registry.resolveDisplayText("example:strategy").string)
        assertEquals("example:unknown", registry.resolveDisplayText("example:unknown").string)
    }
}
