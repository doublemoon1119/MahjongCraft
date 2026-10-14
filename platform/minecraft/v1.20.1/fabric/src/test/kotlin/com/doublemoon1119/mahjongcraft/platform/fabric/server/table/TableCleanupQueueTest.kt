package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.TestCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.createTestAppCoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** [TableCleanupQueue] 的提交順序與失敗隔離測試。 */
class TableCleanupQueueTest {
    /** 提交後立即返回；後一項工作等前一項完成才開始。 */
    @Test
    fun `tasks run one at a time in submission order`() = runTest {
        val queue = queue()
        val log = mutableListOf<String>()
        val releaseFirst = CompletableDeferred<Unit>()

        queue.submit(description = "first") {
            log += "first started"
            releaseFirst.await()
            log += "first finished"
        }
        queue.submit(description = "second") { log += "second" }

        assertEquals(listOf("first started"), log)

        releaseFirst.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("first started", "first finished", "second"), log)
    }

    /** 單一工作失敗只記錄錯誤，後續工作照常執行。 */
    @Test
    fun `a failed task does not stop later tasks`() = runTest {
        val queue = queue()
        val log = mutableListOf<String>()

        queue.submit(description = "failing") { error("cleanup failure") }
        queue.submit(description = "following") { log += "following" }
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("following"), log)
    }

    /**
     * 建立使用測試排程器的清理佇列。
     *
     * @return 工作在測試排程器上執行的佇列。
     */
    private fun TestScope.queue(): TableCleanupQueue {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = TestCoroutineDispatchers(
            default = dispatcher,
            io = dispatcher,
            main = dispatcher,
            aiDecision = dispatcher,
        )
        return TableCleanupQueue(scope = createTestAppCoroutineScope(dispatchers), dispatchers = dispatchers)
    }
}
