package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證桌面物件刷新請求的合併與 session 隔離。 */
class TablePropRefreshQueueTest {
    /** 同一桌的重複載入事件只保留一筆，其他桌仍依登記順序保留。 */
    @Test
    fun `duplicate table requests are coalesced`() {
        val queue = TablePropRefreshQueue()
        val session = Any()
        val first = Uuid.random()
        val second = Uuid.random()
        queue.startSession(session)
        queue.enqueue(session, first)
        queue.enqueue(session, first)
        queue.enqueue(session, second)

        assertEquals(listOf(first, second), queue.drain(session))
        assertFalse(queue.hasPending(session))
    }

    /** 舊 session 的請求不會被新 session 讀取或重新加入。 */
    @Test
    fun `requests do not cross sessions`() {
        val queue = TablePropRefreshQueue()
        val oldSession = Any()
        val newSession = Any()
        val oldTable = Uuid.random()
        val newTable = Uuid.random()
        queue.startSession(oldSession)
        queue.enqueue(oldSession, oldTable)
        queue.startSession(newSession)

        assertTrue(queue.drain(oldSession).isEmpty())
        queue.enqueue(oldSession, oldTable)
        queue.enqueue(newSession, newTable)
        assertEquals(listOf(newTable), queue.drain(newSession))
    }
}
