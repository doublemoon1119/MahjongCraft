package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.autotest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 驗證伺服器自動測試只在明確指定階段時執行。 */
class AutotestServerPhaseTest {
    /** 兩個階段的名稱原樣接受。 */
    @Test
    fun `accepts the start and resume phases`() {
        assertEquals("start", AutotestServerRunner.phaseOf("start"))
        assertEquals("resume", AutotestServerRunner.phaseOf(" resume "))
    }

    /** 沒有指定、空白或不認得的階段都不執行伺服器自動測試。 */
    @Test
    fun `runs nothing without a known phase`() {
        listOf(null, "", "  ", "true", "begin").forEach { value ->
            assertNull(AutotestServerRunner.phaseOf(value), "value '$value'")
        }
    }
}
