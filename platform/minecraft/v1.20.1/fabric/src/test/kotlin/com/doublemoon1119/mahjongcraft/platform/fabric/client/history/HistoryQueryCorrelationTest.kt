package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證新條件、重複回應及世界切換的查詢配對。 */
class HistoryQueryCorrelationTest {
    /** 過期結果不能完成新要求，完成結果不能重複套用。 */
    @Test
    fun `test superseded and duplicate responses are rejected`() {
        val correlation = HistoryQueryCorrelation()
        correlation.begin("old")
        correlation.begin("new")
        assertFalse(correlation.complete("old"))
        assertTrue(correlation.complete("new"))
        assertFalse(correlation.complete("new"))
    }

    /** 世界切換後，原連線的結果不能更新狀態。 */
    @Test
    fun `test disconnected response is rejected`() {
        val correlation = HistoryQueryCorrelation()
        correlation.begin("world-a")
        correlation.clear()
        assertFalse(correlation.complete("world-a"))
        correlation.begin("world-b")
        assertFalse(correlation.complete("world-a"))
        assertTrue(correlation.complete("world-b"))
    }
}
