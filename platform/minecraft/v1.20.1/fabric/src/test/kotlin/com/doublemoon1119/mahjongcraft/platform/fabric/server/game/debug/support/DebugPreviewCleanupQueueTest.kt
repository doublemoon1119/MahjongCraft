package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證 debug 預覽臨時 entity 的到期清除與佇列移除語意。 */
class DebugPreviewCleanupQueueTest {
    /** 尚未到期的工作不應被清除，也不應離開佇列。 */
    @Test
    fun `keeps tasks before their end game time`() {
        val queue = DebugPreviewCleanupQueue()
        var discarded = false
        var now = 10L
        queue.schedule(
            endGameTime = 20L,
            currentGameTime = { now },
            discard = { discarded = true },
        )

        queue.discardExpired()

        assertFalse(discarded)
        assertEquals(1, queue.pendingCount)

        now = 19L
        queue.discardExpired()

        assertFalse(discarded)
        assertEquals(1, queue.pendingCount)
    }

    /** 到期時刻採用「大於等於」判定，清除後工作必須離開佇列。 */
    @Test
    fun `discards tasks once the end game time is reached`() {
        val queue = DebugPreviewCleanupQueue()
        var discardCount = 0
        var now = 20L
        queue.schedule(
            endGameTime = 20L,
            currentGameTime = { now },
            discard = { discardCount++ },
        )

        queue.discardExpired()

        assertEquals(1, discardCount)
        assertEquals(0, queue.pendingCount)

        now = 100L
        queue.discardExpired()

        assertEquals(1, discardCount, "a discarded task must not run twice")
    }

    /** 各工作依自己的時間來源與到期時刻獨立判定，不互相影響。 */
    @Test
    fun `evaluates each task against its own game time`() {
        val queue = DebugPreviewCleanupQueue()
        val discarded = mutableListOf<String>()
        var firstWorldTime = 30L
        val secondWorldTime = 0L
        queue.schedule(
            endGameTime = 30L,
            currentGameTime = { firstWorldTime },
            discard = { discarded += "first" },
        )
        queue.schedule(
            endGameTime = 30L,
            currentGameTime = { secondWorldTime },
            discard = { discarded += "second" },
        )

        queue.discardExpired()

        assertEquals(listOf("first"), discarded)
        assertEquals(1, queue.pendingCount)

        firstWorldTime = 999L
        queue.discardExpired()

        assertEquals(listOf("first"), discarded, "the other task is not expired in its own world time")
        assertTrue(queue.pendingCount == 1)
    }
}
