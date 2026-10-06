package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 驗證歷史牌河公開標記呈現 registry 的登記、查詢與凍結。 */
class HistoryDiscardMarkerDisplayRegistryTest {
    /** 登記的標記可查回呈現方式，未登記的標記查無結果。 */
    @Test
    fun `finds registered displays only`() {
        val display = HistoryDiscardMarkerDisplay(labelTranslationKey = "test.marker", sideways = true)
        val registry = HistoryDiscardMarkerDisplayRegistryImpl().apply { register("example:marker", display) }

        assertEquals(display, registry.find("example:marker"))
        assertNull(registry.find("example:other"))
        assertEquals(setOf("example:marker"), registry.registrationKeys)
    }

    /** 重複 ID、非 namespaced ID 與凍結後登記都會失敗。 */
    @Test
    fun `rejects duplicates invalid ids and late registrations`() {
        val registry = HistoryDiscardMarkerDisplayRegistryImpl().apply { register("example:marker", HistoryDiscardMarkerDisplay("test.marker")) }

        assertFailsWith<IllegalArgumentException> { registry.register("example:marker", HistoryDiscardMarkerDisplay("test.other")) }
        assertFailsWith<IllegalArgumentException> { registry.register("marker", HistoryDiscardMarkerDisplay("test.other")) }
        registry.freeze()
        assertFailsWith<IllegalStateException> { registry.register("example:late", HistoryDiscardMarkerDisplay("test.late")) }
    }
}
