package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證磁碟用量上限的暫停與恢復只在狀態改變時回報。 */
class HistoryDiskLimitTrackerTest {
    /** 一開始在上限內不回報；超過上限回報暫停一次，回到上限內回報恢復一次。 */
    @Test
    fun `transitions are reported once per change`() {
        val tracker = HistoryDiskLimitTracker()
        val session = Uuid.random()

        assertNull(tracker.update(session, storageAvailable = true))
        assertEquals(HistoryDiskLimitTransition.PAUSED, tracker.update(session, storageAvailable = false))
        assertNull(tracker.update(session, storageAvailable = false))
        assertEquals(HistoryDiskLimitTransition.RESUMED, tracker.update(session, storageAvailable = true))
        assertNull(tracker.update(session, storageAvailable = true))
    }

    /** 上一個世界停在超過上限時換到新世界：新世界第一次超過上限仍回報暫停，在上限內也不會誤報恢復。 */
    @Test
    fun `a new session starts from not paused`() {
        val tracker = HistoryDiskLimitTracker()
        val first = Uuid.random()
        tracker.update(first, storageAvailable = false)

        val second = Uuid.random()
        assertNull(tracker.update(second, storageAvailable = true), "A new world within the limit must not report a resume.")
        assertEquals(HistoryDiskLimitTransition.PAUSED, tracker.update(second, storageAvailable = false))

        val third = Uuid.random()
        assertEquals(HistoryDiskLimitTransition.PAUSED, tracker.update(third, storageAvailable = false), "A new world over the limit must report its own pause.")
    }
}
