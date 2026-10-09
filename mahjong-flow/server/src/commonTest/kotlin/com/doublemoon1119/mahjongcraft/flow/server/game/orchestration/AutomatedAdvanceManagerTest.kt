package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [AutomatedAdvanceManager] 的單局推進、補跑、失敗與 session 隔離。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutomatedAdvanceManagerTest {
    private val gameId = Uuid.random()

    /** 請求後立即返回，推進在自己的協程中進行；推進期間視為推進中，結束後釋放。 */
    @Test
    fun `a request advances the game without waiting`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.onAdvance = { gate.await() }

        fixture.manager.request(gameId)
        assertTrue(fixture.manager.isAdvancing(gameId), "The game is registered before the coroutine starts")
        testScheduler.runCurrent()
        assertEquals(1, fixture.advances)

        gate.complete(Unit)
        testScheduler.runCurrent()
        assertFalse(fixture.manager.isAdvancing(gameId))
    }

    /** 不同呼叫端在推進期間請求同一局時，同一時間只有一個推進，推進結束後補跑一輪。 */
    @Test
    fun `requests during an advance run it once more afterwards`() = runTest {
        val fixture = Fixture(this)
        val gates = mutableListOf<CompletableDeferred<Unit>>()
        fixture.onAdvance = { CompletableDeferred<Unit>().also { gates += it }.await() }

        fixture.manager.request(gameId)
        testScheduler.runCurrent()
        repeat(3) { fixture.manager.request(gameId) }
        testScheduler.runCurrent()
        assertEquals(1, fixture.advances, "Requests during an advance must not start another one")
        assertEquals(1, fixture.maxConcurrent)

        gates.last().complete(Unit)
        testScheduler.runCurrent()
        assertEquals(2, fixture.advances, "The requests are served by one more round")
        gates.last().complete(Unit)
        testScheduler.runCurrent()
        assertEquals(2, fixture.advances)
        assertEquals(1, fixture.maxConcurrent)
        assertFalse(fixture.manager.isAdvancing(gameId))
    }

    /** 推進回報需要再一輪時立即再推進。 */
    @Test
    fun `an advance asking for another round runs again`() = runTest {
        val fixture = Fixture(this)
        fixture.again = { fixture.advances < 3 }

        fixture.manager.request(gameId)
        testScheduler.runCurrent()

        assertEquals(3, fixture.advances)
        assertFalse(fixture.manager.isAdvancing(gameId))
    }

    /** 推進失敗時回報並釋放，之後的請求照常推進。 */
    @Test
    fun `a failing advance is reported and released`() = runTest {
        val fixture = Fixture(this)
        fixture.onAdvance = { error("boom") }

        fixture.manager.request(gameId)
        testScheduler.runCurrent()

        assertEquals(listOf(gameId), fixture.failures)
        assertFalse(fixture.manager.isAdvancing(gameId))
        fixture.onAdvance = {}
        fixture.manager.request(gameId)
        testScheduler.runCurrent()
        assertEquals(2, fixture.advances)
    }

    /** 登記後、推進協程開始前作用域就被取消時，推進不執行，登記仍被釋放。 */
    @Test
    fun `an advance cancelled before it starts is released`() = runTest {
        val scope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        val fixture = Fixture(this, scope)

        fixture.manager.request(gameId)
        assertTrue(fixture.manager.isAdvancing(gameId))
        scope.cancel()
        testScheduler.runCurrent()

        assertEquals(0, fixture.advances)
        assertFalse(fixture.manager.isAdvancing(gameId))
    }

    /** 逐步推進在推進中的對局略過；逐步推進期間收到的請求在它結束後推進。 */
    @Test
    fun `exclusive steps skip advancing games and defer requests`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.onAdvance = { gate.await() }
        fixture.manager.request(gameId)
        testScheduler.runCurrent()

        assertNull(fixture.manager.runExclusive(gameId) { "step" })
        gate.complete(Unit)
        testScheduler.runCurrent()

        val stepped = fixture.manager.runExclusive(gameId) {
            fixture.manager.request(gameId)
            "step"
        }
        assertEquals("step", stepped)
        testScheduler.runCurrent()
        assertEquals(2, fixture.advances, "The request made during the step is served after it")
    }

    /** 開始新的 session 後，舊 session 的推進結束時不會清掉新 session 的登記。 */
    @Test
    fun `an old session advance does not release the new one`() = runTest {
        val fixture = Fixture(this)
        val oldGate = CompletableDeferred<Unit>()
        val newGate = CompletableDeferred<Unit>()
        val gates = ArrayDeque(listOf(oldGate, newGate))
        fixture.onAdvance = { gates.removeFirst().await() }
        fixture.manager.request(gameId)
        testScheduler.runCurrent()

        fixture.manager.startSession()
        assertFalse(fixture.manager.isAdvancing(gameId))
        fixture.manager.request(gameId)
        testScheduler.runCurrent()
        oldGate.complete(Unit)
        testScheduler.runCurrent()

        assertTrue(fixture.manager.isAdvancing(gameId), "The new session's advance is still running")
        newGate.complete(Unit)
        testScheduler.runCurrent()
        assertFalse(fixture.manager.isAdvancing(gameId))
    }

    /**
     * 記錄推進次數與同時推進數的管理器。
     *
     * @param test 提供虛擬時間的測試作用域。
     * @param scope 啟動推進協程的作用域。
     */
    private class Fixture(test: TestScope, scope: CoroutineScope = test.backgroundScope) {
        var onAdvance: suspend () -> Unit = {}
        var again: () -> Boolean = { false }
        var advances = 0
        var maxConcurrent = 0
        val failures = mutableListOf<Uuid>()
        private var running = 0

        val manager = AutomatedAdvanceManager(
            scope = scope,
            dispatcher = StandardTestDispatcher(test.testScheduler),
            advanceOnce = {
                advances++
                running++
                maxConcurrent = maxOf(maxConcurrent, running)
                try {
                    onAdvance()
                } finally {
                    running--
                }
                again()
            },
            failureReporter = { gameId, _ -> failures += gameId },
        )
    }
}
