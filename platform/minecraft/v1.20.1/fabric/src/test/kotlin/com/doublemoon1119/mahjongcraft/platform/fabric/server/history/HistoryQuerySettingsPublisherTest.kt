package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.createTestAppCoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證查詢設定 publisher 隨 session 重建且不重複收集設定更新。 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryQuerySettingsPublisherTest {
    /** start→reload→stop→start 只保留目前 session 的一個 collector。 */
    @Test
    fun `publisher restarts with session without duplicate collectors`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = createTestAppCoroutineScope(TestDispatchers(dispatcher))
        val updates = MutableStateFlow(100L)
        val publisher = HistoryQuerySettingsPublisher(scope, TestDispatchers(dispatcher), updates)
        val published = mutableListOf<Long>()

        publisher.start { published += it }
        runCurrent()
        updates.value = 200L
        runCurrent()
        publisher.stop()
        updates.value = 300L
        runCurrent()
        publisher.start { published += it }
        runCurrent()
        publisher.start { published += it }
        runCurrent()
        updates.value = 400L
        runCurrent()
        publisher.stop()

        // 每次 start 重新發布目前值，但下一次設定更新仍只能由一個 collector 發布。
        assertEquals(listOf(100L, 200L, 300L, 300L, 400L), published)
    }

    /**
     * 測試使用的共用 dispatcher 集合。
     *
     * @param dispatcher 測試排程器。
     */
    private class TestDispatchers(dispatcher: CoroutineDispatcher) : CoroutineDispatchers {
        override val default: CoroutineDispatcher = dispatcher

        override val io: CoroutineDispatcher = dispatcher

        override val main: CoroutineDispatcher = dispatcher

        override val aiDecision: CoroutineDispatcher = dispatcher

        override val aiDecisionParallelism: Int = 1
    }
}
