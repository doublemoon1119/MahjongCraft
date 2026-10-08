package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 驗證磁碟用量上限的暫停與恢復只在狀態改變時回報。 */
class HistoryDiskLimitTrackerTest {
    /** 一開始在上限內不回報；超過上限回報暫停一次，回到上限內回報恢復一次。 */
    @Test
    fun `transitions are reported once per change`() {
        val tracker = HistoryDiskLimitTracker()

        assertNull(tracker.update(storageAvailable = true))
        assertEquals(HistoryDiskLimitTransition.PAUSED, tracker.update(storageAvailable = false))
        assertNull(tracker.update(storageAvailable = false))
        assertEquals(HistoryDiskLimitTransition.RESUMED, tracker.update(storageAvailable = true))
        assertNull(tracker.update(storageAvailable = true))
    }
}
