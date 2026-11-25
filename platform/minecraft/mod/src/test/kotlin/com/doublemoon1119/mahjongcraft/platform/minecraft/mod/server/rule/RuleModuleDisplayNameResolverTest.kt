package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.rule

import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證規則模組名稱的文字解析。 */
class RuleModuleDisplayNameResolverTest {
    /** 已登記的規則使用登記的翻譯鍵，未登記時顯示原始 ID。 */
    @Test
    fun `registered rules use their translation key and unknown ones show the raw id`() {
        val registry = RuleModuleDisplayNameRegistryImpl()
        registry.register(RuleModuleDisplayNameDefinition("example:rule", "example.rule.name"))

        assertEquals("example.rule.name", registry.resolveDisplayText("example:rule").string)
        assertEquals("example:unknown", registry.resolveDisplayText("example:unknown").string)
    }
}
