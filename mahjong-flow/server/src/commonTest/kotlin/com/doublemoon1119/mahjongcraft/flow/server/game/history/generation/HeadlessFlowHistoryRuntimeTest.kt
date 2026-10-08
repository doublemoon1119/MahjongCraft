package com.doublemoon1119.mahjongcraft.flow.server.game.history.generation

import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledHeadlessHistoryRegistries
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 驗證無頭真實 runtime 的完整場長流程與歷史背壓契約。 */
@OptIn(ExperimentalCoroutinesApi::class)
class HeadlessFlowHistoryRuntimeTest {
    /** 驗證東風戰可由四個真實 AI 自動完成。 */
    @Test
    fun `east match completes through cold history flow`() = runTest {
        val runtime = HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_EAST, bundledHeadlessHistoryRegistries())
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
        val runtime = HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_HANCHAN, bundledHeadlessHistoryRegistries())
        var terminal = false
        runtimeFlow(runtime) { terminal = it }
        assertTrue(terminal)
    }

    /** 驗證三人東風戰與三人半莊戰可由三個真實 AI 自動完成。 */
    @Test
    fun `three player matches complete through cold history flow`() = runTest {
        listOf(HeadlessHistoryScenario.THREE_PLAYER_RIICHI_EAST, HeadlessHistoryScenario.THREE_PLAYER_RIICHI_HANCHAN).forEach { scenario ->
            val runtime = HeadlessFlowHistoryRuntime.create(scenario, bundledHeadlessHistoryRegistries())
            var terminal = false
            runtimeFlow(runtime) { terminal = it }
            assertTrue(terminal, "$scenario must reach its terminal event")
            assertEquals(3, runtime.store.snapshot().rooms.getValue(runtime.venueId).aiPlayerIds.size)
        }
    }

    /** 驗證計時器量到 AI 決策、快照同步與歷史記錄，歸零後重新累計。 */
    @Test
    fun `step timer measures each stage`() = runTest {
        val timer = HeadlessStepTimer()
        val store = AuthoritativeStateStore(historyRecordingEnabled = true, historyRecordingObserver = timer)
        val runtime = HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_EAST, bundledHeadlessHistoryRegistries(), store, timer)
        assertTrue(timer.historyEvents > 0, "Opening the match must record history events.")

        timer.reset()
        assertEquals(Duration.ZERO, timer.aiDecision + timer.snapshotSync + timer.historyRecording)
        assertEquals(0, timer.historyEvents)
        repeat(STEPS_TO_MEASURE) { runtime.step() }

        assertTrue(timer.aiDecision > Duration.ZERO, "AI turns must be timed.")
        assertTrue(timer.snapshotSync > Duration.ZERO, "Snapshot synchronization must be timed.")
        assertTrue(timer.historyRecording > Duration.ZERO, "History recording must be timed.")
        assertTrue(timer.historyEvents > 0)
        assertNotNull(timer.slowestAiDecision, "The slowest AI decision must be kept for diagnosis.")
        assertNull(timer.ongoingAiDecision, "No AI decision may stay in progress after a step.")

        timer.reset()
        assertNull(timer.slowestAiDecision)
    }

    /** 驗證未確認整批事件時，流程不會推進下一個權威步驟。 */
    @Test
    fun `cold flow requires complete batch acknowledgement`() = runTest {
        val runtime = HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_EAST, bundledHeadlessHistoryRegistries())
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
        val runtime = HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_EAST, bundledHeadlessHistoryRegistries())
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
        val runtime = HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_EAST, bundledHeadlessHistoryRegistries())
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
        val runtime = HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_EAST, bundledHeadlessHistoryRegistries())
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
        val runtime = HeadlessFlowHistoryRuntime.create(HeadlessHistoryScenario.RIICHI_EAST, bundledHeadlessHistoryRegistries())
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

    private companion object {
        /** 計時測試推進的步數；足以涵蓋多次 AI 出牌，又不會塞滿待寫佇列。 */
        const val STEPS_TO_MEASURE = 40
    }
}
