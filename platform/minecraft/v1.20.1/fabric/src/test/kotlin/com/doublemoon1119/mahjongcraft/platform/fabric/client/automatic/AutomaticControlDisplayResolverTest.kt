package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 驗證自動操作顯示 resolver 的穩定排序與未知 ID 降級。 */
class AutomaticControlDisplayResolverTest {
    /** 驗證已登記項目按 order 排列，未知項目按 ID 排在末尾且只警告一次。 */
    @Test
    fun `registered controls sort before stable unknown fallbacks`() {
        val registry = AutomaticControlDisplayRegistryImpl().apply {
            register("example:second", AutomaticControlDisplay("example.second", "example.second.description", 20))
            register("example:first", AutomaticControlDisplay("example.first", "example.first.description", 10))
            freeze()
        }
        val resolver = AutomaticControlDisplayResolver(registry)

        val resolved = resolver.resolveAll(
            listOf("example:unknown_b", "example:second", "example:unknown_a", "example:first", "example:unknown_a"),
        )

        assertEquals(
            listOf("example:first", "example:second", "example:unknown_a", "example:unknown_b"),
            resolved.map { it.controlId },
        )
        assertEquals("example:unknown_a", resolved[2].label.string)
        assertNull(resolved[2].description)
        resolver.resolve("example:unknown_a")
        assertEquals(setOf("example:unknown_a", "example:unknown_b"), resolver.warnedUnknownControlIds)
    }
}
