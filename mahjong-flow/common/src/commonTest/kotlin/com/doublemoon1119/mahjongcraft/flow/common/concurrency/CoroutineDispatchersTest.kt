package com.doublemoon1119.mahjongcraft.flow.common.concurrency

import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.TestCoroutineDispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 驗證 [CoroutineDispatchers] 介面與其測試實作 [TestCoroutineDispatchers] 的整合情況。
 */
class CoroutineDispatchersTest {

    /**
     * 測試測試調度器是否能確保協程任務同步執行。
     */
    @Test
    fun `test dispatchers synchronization with test implementation`() = runTest {
        // 注入測試用的調度器
        val dispatchers: CoroutineDispatchers = TestCoroutineDispatchers()
        var result = 0

        // 使用其中的 io 調度器執行任務
        // 由於使用的是 UnconfinedTestDispatcher，這應該會立即執行
        val job = async(dispatchers.io) {
            result = 42
        }
        job.await()

        // 驗證結果是否正確寫入
        assertEquals(42, result)
    }
}
