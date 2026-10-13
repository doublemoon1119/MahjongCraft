package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistryImpl
import net.minecraft.text.Text
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證移除純委派 registry 後，既有 JVM 文字解析入口仍可呼叫。 */
class DisplayNameResolverFacadeTest {
    /** AI 策略的既有靜態入口仍解析已登記名稱，未登記時回傳原 key。 */
    @Test
    fun `ai display text keeps its existing jvm facade`() {
        val registry = AiStrategyDisplayNameRegistryImpl()
        registry.register("example:strategy", "example.strategy.name")
        val method = Class.forName("com.doublemoon1119.mahjongcraft.platform.fabric.server.room.FabricAiStrategyDisplayNameRegistryKt")
            .getMethod("resolveDisplayText", AiStrategyDisplayNameRegistry::class.java, String::class.java)

        assertEquals("example.strategy.name", (method.invoke(null, registry, "example:strategy") as Text).string)
        assertEquals("example:unknown", (method.invoke(null, registry, "example:unknown") as Text).string)
    }

    /** 規則名稱的既有靜態入口仍解析已登記名稱，未登記時回傳原 ID。 */
    @Test
    fun `rule display text keeps its existing jvm facade`() {
        val registry = RuleModuleDisplayNameRegistryImpl()
        registry.register("example:rule", "example.rule.name")
        val method = Class.forName("com.doublemoon1119.mahjongcraft.platform.fabric.server.rule.FabricRuleModuleDisplayNameRegistryKt")
            .getMethod("resolveDisplayText", RuleModuleDisplayNameRegistry::class.java, String::class.java)

        assertEquals("example.rule.name", (method.invoke(null, registry, "example:rule") as Text).string)
        assertEquals("example:unknown", (method.invoke(null, registry, "example:unknown") as Text).string)
    }
}
