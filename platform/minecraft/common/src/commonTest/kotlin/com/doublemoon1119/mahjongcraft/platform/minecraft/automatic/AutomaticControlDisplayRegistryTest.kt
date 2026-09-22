package com.doublemoon1119.mahjongcraft.platform.minecraft.automatic

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證自動操作顯示 registry 的登記、驗證與凍結契約。 */
class AutomaticControlDisplayRegistryTest {
    /** 驗證內建項目的翻譯鍵及固定順序。 */
    @Test
    fun `built-in automatic controls have stable displays`() {
        val registry = AutomaticControlDisplayRegistryImpl().apply {
            registerBuiltInAutomaticControlDisplays()
            freeze()
        }

        assertEquals(
            MinecraftClientConfigScreenKeys.AUTO_SORT_HAND,
            registry.find(BuiltInMinecraftAutomaticControlIds.AUTO_SORT_HAND)?.labelTranslationKey,
        )
        assertEquals(10, registry.find(BuiltInAutomaticControlIds.AUTO_WIN)?.displayOrder)
        assertEquals(20, registry.find(BuiltInAutomaticControlIds.DECLINE_CALLS)?.displayOrder)
        assertEquals(30, registry.find(BuiltInAutomaticControlIds.AUTO_TSUMOGIRI)?.displayOrder)
        assertTrue(registry.isFrozen)
    }

    /** 驗證非法 ID、空白翻譯鍵、重複 ID 與凍結後登記都會失敗。 */
    @Test
    fun `registry rejects invalid duplicate and late registrations`() {
        val registry = AutomaticControlDisplayRegistryImpl()
        val display = AutomaticControlDisplay("example.label", "example.description", 5)

        assertFailsWith<IllegalArgumentException> { registry.register("invalid", display) }
        assertFailsWith<IllegalArgumentException> { AutomaticControlDisplay(" ", "example.description", 5) }
        assertFailsWith<IllegalArgumentException> { AutomaticControlDisplay("example.label", " ", 5) }

        registry.register("example:control", display)
        assertFailsWith<IllegalArgumentException> { registry.register("example:control", display) }
        assertNull(registry.find("example:missing"))

        registry.freeze()
        assertFailsWith<IllegalStateException> { registry.register("example:late", display) }
    }
}
