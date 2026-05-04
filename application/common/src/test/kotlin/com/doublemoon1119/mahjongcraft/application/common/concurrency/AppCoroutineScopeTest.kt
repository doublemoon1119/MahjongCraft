package com.doublemoon1119.mahjongcraft.application.common.concurrency

import com.doublemoon1119.mahjongcraft.testing.application.common.concurrency.TestCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.testing.application.common.concurrency.createTestAppCoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 驗證 [AppCoroutineScope] 的生命週期與異常處理行為。
 */
class AppCoroutineScopeTest {

    /**
     * 測試當子協程發生異常時，透過注入的 [CoroutineExceptionHandler] 捕獲異常，並驗證作用域是否依然保持活躍。
     */
    @Test
    fun `test scope remains active when child fails`() = runTest {
        var exceptionCaught = false
        val handler = CoroutineExceptionHandler { _, _ -> exceptionCaught = true }

        // 使用工廠方法注入自定義 handler
        val scope = createTestAppCoroutineScope(exceptionHandler = handler)

        // 啟動一個會崩潰的協程，並加上 handler
        scope.launch(handler) {
            throw RuntimeException("Simulated failure")
        }

        // 啟動另一個正常的協程驗證作用域狀態
        var wasSecondTaskExecuted = false
        scope.launch {
            wasSecondTaskExecuted = true
        }

        // 驗證第一個崩潰後，作用域本身依然是活躍的
        assertTrue(exceptionCaught)
        assertTrue(scope.isActive)
        assertTrue(wasSecondTaskExecuted)

        scope.cancel()
    }

    /**
     * 測試呼叫取消方法後，作用域是否正確關閉。
     */
    @Test
    fun `test scope cancellation`() = runTest {
        val dispatchers = TestCoroutineDispatchers()
        val scope = AppCoroutineScopeImpl(dispatchers)

        scope.cancel()

        assertFalse(scope.isActive)
    }
}
