package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** [MainThreadTaskQueue] 的執行批次與關服等待測試。 */
class MainThreadTaskQueueTest {
    /** 一次處理只執行呼叫當下已排入的工作，處理期間新排入的工作留給下一次。 */
    @Test
    fun `drain runs only the tasks queued when it started`() {
        val queue = MainThreadTaskQueue()
        val log = mutableListOf<String>()
        queue.enqueue {
            log += "first"
            queue.enqueue { log += "added while draining" }
        }
        queue.enqueue { log += "second" }

        assertEquals(2, queue.drain())
        assertEquals(listOf("first", "second"), log)

        assertEquals(1, queue.drain())
        assertEquals(listOf("first", "second", "added while draining"), log)
    }

    /** 等待期間由其他執行緒排入的工作會被執行，完成條件成立後立即返回。 */
    @Test
    fun `drainUntil runs tasks queued by other threads until the condition holds`() {
        val queue = MainThreadTaskQueue()
        val worker = Executors.newSingleThreadExecutor()
        var done = false
        try {
            worker.execute {
                Thread.sleep(50)
                queue.enqueue { done = true }
            }

            assertTrue(queue.drainUntil(isDone = { done }, timeout = 5.seconds))
        } finally {
            worker.shutdownNow()
            worker.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    /** 完成條件一直不成立時在時限後返回 false，不會無限等待。 */
    @Test
    fun `drainUntil gives up after the timeout`() {
        val queue = MainThreadTaskQueue()

        assertFalse(queue.drainUntil(isDone = { false }, timeout = 100.milliseconds))
    }
}
