package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證公開 indicator 顯示 registry 的 namespaced ID 與凍結契約。 */
class PublicPlayerIndicatorDisplayRegistryTest {
    @Test
    fun `registers and freezes displays`() {
        val registry = PublicPlayerIndicatorDisplayRegistryImpl()
        val display = PublicPlayerIndicatorDisplay("test.indicator", 0x123456)
        registry.register("test:indicator", display)
        assertEquals(display, registry.find("test:indicator"))
        registry.freeze()
        assertFailsWith<IllegalStateException> {
            registry.register("test:late", display)
        }
    }

    @Test
    fun `rejects invalid IDs and duplicate displays`() {
        val registry = PublicPlayerIndicatorDisplayRegistryImpl()
        val display = PublicPlayerIndicatorDisplay("test.indicator")
        assertFailsWith<IllegalArgumentException> { registry.register("indicator", display) }
        registry.register("test:indicator", display)
        assertFailsWith<IllegalArgumentException> { registry.register("test:indicator", display) }
    }
}
