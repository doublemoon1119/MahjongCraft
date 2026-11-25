package com.doublemoon1119.mahjongcraft.buildlogic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證 `mahjongcraftAutotest` 的開關判定與 debug 模組讀取系統屬性時相同。 */
class AutotestSwitchTest {
    /** 沒有設定、空白與 false（不分大小寫）都不開啟自動測試。 */
    @Test
    fun `missing, blank and false values leave the tests off`() {
        listOf(null, "", "  ", "false", "FALSE", " False ").forEach { value ->
            assertFalse(isAutotestEnabled(value), "value '$value'")
        }
    }

    /** true、all 與步驟清單都開啟自動測試。 */
    @Test
    fun `true, all and step lists turn the tests on`() {
        listOf("true", "TRUE", "all", "table,replay", "world").forEach { value ->
            assertTrue(isAutotestEnabled(value), "value '$value'")
        }
    }
}
