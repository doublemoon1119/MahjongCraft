package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證保存狀態要求只能接受目前 request 的回覆。 */
class HistoryArchiveStatusTransportTest {
    /** 舊 request、重複完成及取消都不能污染目前配對。 */
    @Test
    fun `test archive status correlation accepts only active request`() {
        val correlation = HistoryArchiveStatusCorrelation()
        correlation.begin("one", "match-one")
        assertFalse(correlation.complete("old"))
        assertTrue(correlation.complete("one"))
        assertFalse(correlation.complete("one"))

        correlation.begin("two", "match-two")
        assertFalse(correlation.cancel("old"))
        assertTrue(correlation.cancel("two"))
        assertFalse(correlation.cancel("two"))
    }
}
