package com.doublemoon1119.mahjongcraft.flow.server.game.history.generation

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.ai.registerBuiltInAiStrategies
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** 驗證無頭真實 runtime 的完整場長流程與歷史背壓契約。 */
@OptIn(ExperimentalCoroutinesApi::class)
class HeadlessFlowHistoryRuntimeTest {
    /** 驗證東風戰可由四個真實 AI 自動完成。 */
    @Test
    fun `east match completes through cold history flow`() = runTest {
        val runtime = HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_EAST, strategies())
        var terminal = false
        runtimeFlow(runtime) { terminal = it }
        assertTrue(terminal)
        val room = runtime.store.snapshot().rooms.getValue(runtime.venueId)
        assertTrue(room.canStart, "The returned AI room must remain ready to start another match.")
        assertEquals(4, room.aiPlayerIds.size)
        assertTrue(room.hostId !in room.readyPlayerIds, "The host must remain outside the readiness collection.")
    }

    /** 驗證半莊戰可由四個真實 AI 自動完成。 */
    @Test
    fun `hanchan match completes through cold history flow`() = runTest {
        val runtime = HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_HANCHAN, strategies())
        var terminal = false
        runtimeFlow(runtime) { terminal = it }
        assertTrue(terminal)
    }

    /** 驗證未確認整批事件時，流程不會推進下一個權威步驟。 */
    @Test
    fun `cold flow requires complete batch acknowledgement`() = runTest {
        val runtime = HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_EAST, strategies())
        val pendingBefore = runtime.store.snapshot().historyRecordingState.pendingEvents.size
        var failed = false
        var message = ""
        try {
            HeadlessHistoryMatchRunner(runtime).events().collect { }
        } catch (error: IllegalStateException) {
            failed = true
            message = error.message.orEmpty()
        }
        assertTrue(failed)
        assertTrue(message.contains("acknowledge the entire emitted batch"))
        assertEquals(pendingBefore, runtime.store.snapshot().historyRecordingState.pendingEvents.size)
    }

    /** 驗證 collector 尚未完成確認時，流程會停在同一批事件而不推進權威步驟。 */
    @Test
    fun `cold flow waits for collector before advancing`() = runTest {
        val runtime = HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_EAST, strategies())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val before = runtime.store.snapshot().historyRecordingState.pendingEvents.size
        val job = launch {
            HeadlessHistoryMatchRunner(runtime).events().collect { progress ->
                entered.complete(Unit)
                release.await()
                runtime.store.acknowledgeHistoryEvents(progress.events.map { it.matchId to it.sequence }.toSet())
            }
        }
        entered.await()
        advanceUntilIdle()
        assertEquals(before, runtime.store.snapshot().historyRecordingState.pendingEvents.size)
        release.complete(Unit)
        job.join()
    }

    /** 驗證步數上限在初始交易確認後仍會阻止無界推進。 */
    @Test
    fun `runner enforces maximum steps`() = runTest {
        val runtime = HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_EAST, strategies())
        var failed = false
        try {
            HeadlessHistoryMatchRunner(runtime, HeadlessHistoryRunLimits(maxSteps = 1)).events().collect { progress ->
                runtime.store.acknowledgeHistoryEvents(progress.events.map { it.matchId to it.sequence }.toSet())
            }
        } catch (error: IllegalStateException) {
            failed = error.message.orEmpty().contains("step limit")
        }
        assertTrue(failed)
    }

    /** 驗證收集端超過保存等待期限時，來源批次仍然保留。 */
    @Test
    fun `runner times out a stalled collector without dropping events`() = runTest {
        val runtime = HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_EAST, strategies())
        val before = runtime.store.snapshot().historyRecordingState.pendingEvents.size
        var timedOut = false
        try {
            HeadlessHistoryMatchRunner(runtime).events().collect {
                delay(61.seconds)
            }
        } catch (_: TimeoutCancellationException) {
            timedOut = true
        }
        assertTrue(timedOut)
        assertEquals(before, runtime.store.snapshot().historyRecordingState.pendingEvents.size)
    }

    /** 驗證收集取消不會確認或刪除尚未完成的來源批次。 */
    @Test
    fun `runner cancellation retains pending events`() = runTest {
        val runtime = HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_EAST, strategies())
        val before = runtime.store.snapshot().historyRecordingState.pendingEvents.size
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            HeadlessHistoryMatchRunner(runtime).events().collect {
                entered.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        entered.await()
        job.cancel()
        job.join()
        assertEquals(before, runtime.store.snapshot().historyRecordingState.pendingEvents.size)
    }

    /**
     * 收集流程並在每批事件後確認完整事件交易。
     *
     * @param runtime 欲推進的隔離 runtime。
     * @param terminal 終局進度回呼。
     */
    private suspend fun runtimeFlow(runtime: HeadlessFlowHistoryRuntime, terminal: (Boolean) -> Unit) {
        HeadlessHistoryMatchRunner(runtime).events().collect { progress ->
            val ids = progress.events.map { it.matchId to it.sequence }.toSet()
            runtime.store.acknowledgeHistoryEvents(ids)
            if (progress.terminal && progress.events.isEmpty()) terminal(true)
        }
    }

    /**
     * 建立內建策略 registry。
     *
     * @return 含內建策略的 registry。
     */
    private fun strategies() = MahjongAiStrategyRegistryImpl(BuiltInAiStrategyKeys.BEGINNER).apply {
        val modules = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        registerBuiltInAiStrategies(modules, ExtensionGameActionAiRegistry(), OpponentModelRegistry())
    }
}
