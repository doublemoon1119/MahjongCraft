package com.doublemoon1119.mahjongcraft.platform.fabric.logging

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證反覆讀取失敗時只對不同的失敗值記錄。 */
class DistinctFailureGateTest {
    /** 同一個失敗值只記錄一次；換成不同的值會再記錄。 */
    @Test
    fun `same failure is reported once`() {
        val gate = DistinctFailureGate<String>()

        assertTrue(gate.shouldReport("broken"))
        assertFalse(gate.shouldReport("broken"))
        assertTrue(gate.shouldReport("other"))
        assertFalse(gate.shouldReport("other"))
    }

    /** 中間成功讀取過，之後同樣的失敗值會再記錄。 */
    @Test
    fun `success resets the gate`() {
        val gate = DistinctFailureGate<String>()
        gate.shouldReport("broken")

        gate.clear()

        assertTrue(gate.shouldReport("broken"))
    }
}
