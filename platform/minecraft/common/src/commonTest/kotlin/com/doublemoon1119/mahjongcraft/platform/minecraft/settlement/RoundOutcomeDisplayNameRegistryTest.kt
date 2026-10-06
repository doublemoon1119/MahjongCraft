package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 驗證規則特殊本局結果名稱 registry 的登記、查詢與凍結。 */
class RoundOutcomeDisplayNameRegistryTest {
    /** 登記的結果可查回翻譯鍵，未登記的結果查無結果。 */
    @Test
    fun `finds registered names only`() {
        val registry = RoundOutcomeDisplayNameRegistryImpl().apply { register("example:special", "test.special") }

        assertEquals("test.special", registry.find("example:special"))
        assertNull(registry.find("example:other"))
        assertEquals(setOf("example:special"), registry.registrationKeys)
    }

    /** 重複 ID、非 namespaced ID 與凍結後登記都會失敗。 */
    @Test
    fun `rejects duplicates invalid ids and late registrations`() {
        val registry = RoundOutcomeDisplayNameRegistryImpl().apply { register("example:special", "test.special") }

        assertFailsWith<IllegalArgumentException> { registry.register("example:special", "test.other") }
        assertFailsWith<IllegalArgumentException> { registry.register("special", "test.other") }
        registry.freeze()
        assertFailsWith<IllegalStateException> { registry.register("example:late", "test.late") }
    }
}
