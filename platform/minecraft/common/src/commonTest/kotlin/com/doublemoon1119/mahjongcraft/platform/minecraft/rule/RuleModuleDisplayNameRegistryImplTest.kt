package com.doublemoon1119.mahjongcraft.platform.minecraft.rule

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證規則名稱登記表的登記順序。 */
class RuleModuleDisplayNameRegistryImplTest {
    /** 已登記的規則 ID 依登記順序列出，不依字母重新排序，供規則選單沿用。 */
    @Test
    fun `registration keys keep registration order`() {
        val registry = RuleModuleDisplayNameRegistryImpl()
        listOf("example:zeta", "example:alpha", "example:mid").forEach { id ->
            registry.register(RuleModuleDisplayNameDefinition(ruleModuleId = id, translationKey = "$id.name"))
        }

        assertEquals(listOf("example:zeta", "example:alpha", "example:mid"), registry.registrationKeys.toList())
    }
}
