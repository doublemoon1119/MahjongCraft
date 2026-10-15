package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.TestCoroutineDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/** [FabricSessionBackgroundScope] 的 session 生命週期測試。 */
class FabricSessionBackgroundScopeTest {
    /** 取消時立即取消常駐的背景工作，不等待它們結束；新 session 換成可用的新 Job。 */
    @Test
    fun `cancel stops long-running background work and a new session starts fresh`() {
        val scope = FabricSessionBackgroundScope(TestCoroutineDispatchers(default = Dispatchers.Unconfined))
        val longRunning = scope.launch { awaitCancellation() }
        val initialJob = requireNotNull(scope.coroutineContext[Job])

        scope.cancel()

        assertTrue(longRunning.isCancelled)
        scope.startSession()
        val nextJob = requireNotNull(scope.coroutineContext[Job])
        assertNotSame(initialJob, nextJob)
        assertTrue(nextJob.isActive)
    }
}
