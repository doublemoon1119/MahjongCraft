package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/** [FabricAppCoroutineScope] 對應 integrated/dedicated server session 的生命週期測試。 */
class FabricAppCoroutineScopeTest {
    /** 新 server session 必須取消舊 Job 並換成可用的新 Job，停止時再取消目前 Job。 */
    @Test
    fun `test starting a new server session replaces the cancelled job`() {
        val scope = FabricAppCoroutineScope(UnconfinedCoroutineDispatchers)
        val initialJob = requireNotNull(scope.coroutineContext[Job])

        scope.startSession()

        val nextJob = requireNotNull(scope.coroutineContext[Job])
        assertNotSame(initialJob, nextJob)
        assertTrue(initialJob.isCancelled)
        assertTrue(nextJob.isActive)

        scope.cancel()
        assertTrue(nextJob.isCancelled)
    }

    /** 不需要 Minecraft 執行緒即可驗證 Job 替換語意的測試 dispatcher。 */
    private object UnconfinedCoroutineDispatchers : CoroutineDispatchers {
        /** CPU 工作使用不分派的測試 dispatcher。 */
        override val default: CoroutineDispatcher = Dispatchers.Unconfined

        /** I/O 工作使用不分派的測試 dispatcher。 */
        override val io: CoroutineDispatcher = Dispatchers.Unconfined

        /** 主執行緒工作使用不分派的測試 dispatcher。 */
        override val main: CoroutineDispatcher = Dispatchers.Unconfined

        /** AI 決策工作使用不分派的測試 dispatcher。 */
        override val aiDecision: CoroutineDispatcher = Dispatchers.Unconfined

        /** 測試允許的 AI 決策平行工作數。 */
        override val aiDecisionParallelism = 1
    }
}
