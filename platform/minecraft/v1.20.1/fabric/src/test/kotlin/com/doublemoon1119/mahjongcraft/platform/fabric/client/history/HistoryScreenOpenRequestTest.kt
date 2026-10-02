package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證延後開啟畫面時的連線身分與世代邊界。 */
class HistoryScreenOpenRequestTest {
    /** 只有原連線與原世代可以接續開啟。 */
    @Test
    fun `test deferred open rejects disconnected or replaced session`() {
        val connection = Any()
        val request = OpenRequest(null, connection, 4L)
        assertTrue(request.isValid(connection, 4L))
        assertFalse(request.isValid(null, 4L))
        assertFalse(request.isValid(Any(), 4L))
        assertFalse(request.isValid(connection, 5L))
    }

    /** 聊天排名入口可攜帶指定對局，但不改變連線失效判定。 */
    @Test
    fun `test deferred open preserves optional match id`() {
        val request = OpenRequest(null, Any(), 7L, "match-id")
        assertEquals("match-id", request.matchId)
    }
}
