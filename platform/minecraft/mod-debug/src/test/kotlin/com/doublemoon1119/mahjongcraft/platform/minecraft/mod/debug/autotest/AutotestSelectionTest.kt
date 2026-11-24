package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證開發執行的自動測試設定如何決定要執行的步驟。 */
class AutotestSelectionTest {
    /** 沒有設定、空白或 false 時不執行自動測試。 */
    @Test
    fun `missing, blank or false disables the autotest`() {
        listOf(null, "", "  ", "false", "FALSE").forEach { assertNull(AutotestSelection.parse(it), "value: $it") }
    }

    /** true 或 all 時執行全部步驟。 */
    @Test
    fun `true or all selects every step`() {
        listOf("true", "all", " All ").forEach { value ->
            val selection = AutotestSelection.parse(value)
            assertTrue(selection?.includes("anything") == true, "value: $value")
        }
    }

    /** 其他值是以逗號分隔的步驟識別碼，只執行列出的步驟。 */
    @Test
    fun `a comma separated list selects only the listed steps`() {
        val selection = AutotestSelection.parse("screens, resources,,")

        assertEquals(setOf("screens", "resources"), selection?.stepIds)
        assertTrue(selection!!.includes("screens"))
        assertFalse(selection.includes("table"))
    }
}
