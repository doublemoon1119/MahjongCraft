package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** [AiDecisionExecutor] 的背景執行、等待上限、名額與同一局的呼叫限制。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiDecisionExecutorTest {
    private val gameId = Uuid.random()
    private val otherGameId = Uuid.random()
    private val playerId = Uuid.random()

    /** 策略在執行器的調度器上計算，結果原樣回傳。 */
    @Test
    fun `the strategy runs on the decision dispatcher`() = runTest {
        val dispatcher = RecordingDispatcher(StandardTestDispatcher(testScheduler))
        val executor = AiDecisionExecutor(dispatcher, capacity = 1)
        var interceptor: ContinuationInterceptor? = null

        val result = executor.decide(gameId, playerId, STRATEGY_KEY, decide = {
            interceptor = currentCoroutineContext()[ContinuationInterceptor]
            "decided"
        }, fallback = { "fallback" })

        assertEquals("decided", result)
        assertSame(dispatcher, interceptor)
        assertTrue(dispatcher.dispatches > 0)
    }

    /** 等待超過上限時改用固定結果並回報，不等待不理會取消的策略結束。 */
    @Test
    fun `a decision past the timeout uses the fallback without waiting for the strategy`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<Unit>()

        val result = fixture.executor.decide(gameId, playerId, STRATEGY_KEY, decide = { fixture.uncooperative(gate) }, fallback = { "fallback" })

        assertEquals("fallback", result)
        assertEquals(listOf("timeout:$gameId"), fixture.reporter.events)
        assertEquals(TIMEOUT.inWholeMilliseconds, testScheduler.currentTime)
        assertFalse(gate.isCompleted, "The strategy must still be running")
        gate.complete(Unit)
    }

    /** 逾時但尚未結束的策略工作仍占用名額，名額要等它真正結束才釋放。 */
    @Test
    fun `an abandoned strategy keeps its permit until it ends`() = runTest {
        val fixture = Fixture(this, capacity = 1)
        val gate = CompletableDeferred<Unit>()
        fixture.executor.decide(gameId, playerId, STRATEGY_KEY, decide = { fixture.uncooperative(gate) }, fallback = { "fallback" })

        var otherRan = false
        val blocked = fixture.executor.decide(otherGameId, playerId, STRATEGY_KEY, decide = {
            otherRan = true
            "decided"
        }, fallback = { "fallback" })
        assertEquals("fallback", blocked, "Another game must wait for the permit until the timeout")
        assertFalse(otherRan, "Work waiting for a permit must not start after the timeout")

        gate.complete(Unit)
        testScheduler.runCurrent()
        val afterRelease = fixture.executor.decide(otherGameId, playerId, STRATEGY_KEY, decide = { "decided" }, fallback = { "fallback" })
        assertEquals("decided", afterRelease)
    }

    /** 同一局已有策略正在正常計算時，這次不決策、不呼叫策略，也不改用固定結果。 */
    @Test
    fun `a game with a strategy still computing is skipped`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<String>()
        val first = async { fixture.executor.decide(gameId, playerId, STRATEGY_KEY, decide = { gate.await() }, fallback = { "fallback" }) }
        testScheduler.runCurrent()

        var secondCalled = false
        val second = fixture.executor.decide(gameId, playerId, STRATEGY_KEY, decide = {
            secondCalled = true
            "second"
        }, fallback = { "fallback" })

        assertNull(second)
        assertFalse(secondCalled)
        assertEquals(emptyList(), fixture.reporter.events)
        gate.complete("first")
        assertEquals("first", first.await())
    }

    /** 同一局先前的呼叫逾時但尚未結束時，之後直接使用固定結果並只回報一次；呼叫結束後恢復詢問策略。 */
    @Test
    fun `a game with an abandoned strategy uses the fallback until it ends`() = runTest {
        val fixture = Fixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.executor.decide(gameId, playerId, STRATEGY_KEY, decide = { fixture.uncooperative(gate) }, fallback = { "fallback" })

        var called = 0
        repeat(2) {
            val result = fixture.executor.decide(gameId, playerId, STRATEGY_KEY, decide = {
                called++
                "decided"
            }, fallback = { "fallback" })
            assertEquals("fallback", result)
        }
        assertEquals(0, called)
        assertEquals(listOf("timeout:$gameId", "still-running:$gameId"), fixture.reporter.events)

        gate.complete(Unit)
        testScheduler.runCurrent()
        val afterEnd = fixture.executor.decide(gameId, playerId, STRATEGY_KEY, decide = { "decided" }, fallback = { "fallback" })
        assertEquals("decided", afterEnd)
    }

    /** 取得名額後、策略工作開始執行前被取消時，策略本文不執行，名額與這一局的記錄恰好釋放一次。 */
    @Test
    fun `a decision cancelled before the strategy starts releases its permit`() = runTest {
        val held = HeldDispatcher()
        val executor = AiDecisionExecutor(held, capacity = 1, timeout = TIMEOUT)
        var started = false
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            executor.decide(gameId, playerId, STRATEGY_KEY, decide = {
                started = true
                "decided"
            }, fallback = { "fallback" })
        }
        // 呼叫端已取得名額並建立工作，工作停在調度器中尚未執行；此時取消呼叫端。
        waiting.cancel()
        testScheduler.runCurrent()
        held.runAll()

        assertFalse(started)
        assertTrue(waiting.isCancelled)
        held.runEach = true
        assertEquals("next", executor.decide(gameId, playerId, STRATEGY_KEY, decide = { "next" }, fallback = { "fallback" }))
        assertEquals("other", executor.decide(otherGameId, playerId, STRATEGY_KEY, decide = { "other" }, fallback = { "fallback" }))
    }

    /** 等待結果的協程被取消（例如 server session 結束）時，合作的策略跟著取消，這一局之後照常詢問策略。 */
    @Test
    fun `a cancelled caller cancels a cooperative strategy`() = runTest {
        val fixture = Fixture(this)
        val waiting = async { fixture.executor.decide(gameId, playerId, STRATEGY_KEY, decide = { awaitCancellation() }, fallback = { "fallback" }) }
        testScheduler.runCurrent()
        waiting.cancel()
        testScheduler.runCurrent()

        assertEquals("next", fixture.executor.decide(gameId, playerId, STRATEGY_KEY, decide = { "next" }, fallback = { "fallback" }))
        assertEquals(emptyList(), fixture.reporter.events)
    }

    /**
     * 等待結果的協程被取消、而策略不理會取消時，結果不會交給任何人；這一局在它結束前使用固定結果，名額也一直占用。
     *
     * 這是舊 server session 留下不合作工作的情況：它不能提交結果，但仍計入名額上限。
     */
    @Test
    fun `an uncooperative strategy outliving its caller still counts`() = runTest {
        val fixture = Fixture(this, capacity = 1)
        val gate = CompletableDeferred<Unit>()
        val waiting = async { fixture.executor.decide(gameId, playerId, STRATEGY_KEY, decide = { fixture.uncooperative(gate) }, fallback = { "fallback" }) }
        testScheduler.runCurrent()
        waiting.cancel()
        testScheduler.runCurrent()

        assertEquals("fallback", fixture.executor.decide(gameId, playerId, STRATEGY_KEY, decide = { "decided" }, fallback = { "fallback" }))
        assertEquals("fallback", fixture.executor.decide(otherGameId, playerId, STRATEGY_KEY, decide = { "decided" }, fallback = { "fallback" }))

        gate.complete(Unit)
        testScheduler.runCurrent()
        assertEquals("decided", fixture.executor.decide(otherGameId, playerId, STRATEGY_KEY, decide = { "decided" }, fallback = { "fallback" }))
    }

    /** 每次有結果的決策都回報結果的來源與延遲；同一局正在計算而略過的請求不回報。尚未結束的策略工作數包含逾時後仍在執行的工作。 */
    @Test
    fun `decisions with a result are observed with their latency`() = runTest {
        val observed = mutableListOf<Pair<AiDecisionOutcome, Long>>()
        val executor = AiDecisionExecutor(
            StandardTestDispatcher(testScheduler),
            capacity = 4,
            timeout = TIMEOUT,
            observer = { _, outcome, latency -> observed += outcome to latency.inWholeMilliseconds },
            timeSource = testScheduler.timeSource,
        )
        val gate = CompletableDeferred<Unit>()

        assertEquals("decided", executor.decide(gameId, playerId, STRATEGY_KEY, decide = { "decided" }, fallback = { "fallback" }))
        executor.decide(gameId, playerId, STRATEGY_KEY, decide = {
            withContext(NonCancellable) { gate.await() }
            "late"
        }, fallback = { "fallback" })
        assertEquals(1, executor.unfinishedStrategyCalls, "The timed out call is still running")
        executor.decide(gameId, playerId, STRATEGY_KEY, decide = { "decided" }, fallback = { "fallback" })

        assertEquals(
            listOf(AiDecisionOutcome.DECIDED to 0L, AiDecisionOutcome.TIMED_OUT to TIMEOUT.inWholeMilliseconds, AiDecisionOutcome.PREVIOUS_STILL_RUNNING to 0L),
            observed,
        )
        gate.complete(Unit)
        testScheduler.runCurrent()
        assertEquals(0, executor.unfinishedStrategyCalls)
    }

    /** 兩次讀取峰值之間開始又全部結束的策略工作仍計入峰值；讀取後從當下的數量重新起算。 */
    @Test
    fun `a short burst between two peak reads is counted`() = runTest {
        val executor = AiDecisionExecutor(StandardTestDispatcher(testScheduler), capacity = 4, timeout = TIMEOUT)
        val gate = CompletableDeferred<String>()
        assertEquals(0, executor.takeUnfinishedStrategyPeak())

        val burst = List(3) { async { executor.decide(Uuid.random(), playerId, STRATEGY_KEY, decide = { gate.await() }, fallback = { "fallback" }) } }
        testScheduler.runCurrent()
        gate.complete("decided")
        burst.forEach { assertEquals("decided", it.await()) }
        testScheduler.runCurrent()

        assertEquals(0, executor.unfinishedStrategyCalls)
        assertEquals(3, executor.takeUnfinishedStrategyPeak(), "The burst ended before the read but must still be counted")
        assertEquals(0, executor.takeUnfinishedStrategyPeak())
    }

    /** 讀取峰值時仍未結束的工作成為下一段的起算點，不會被歸零漏掉。 */
    @Test
    fun `the peak restarts from the calls still running`() = runTest {
        val executor = AiDecisionExecutor(StandardTestDispatcher(testScheduler), capacity = 4, timeout = TIMEOUT)
        val first = CompletableDeferred<String>()
        val second = CompletableDeferred<String>()
        val running = listOf(first, second).map { gate ->
            async { executor.decide(Uuid.random(), playerId, STRATEGY_KEY, decide = { gate.await() }, fallback = { "fallback" }) }
        }
        testScheduler.runCurrent()
        assertEquals(2, executor.takeUnfinishedStrategyPeak())

        first.complete("decided")
        running.first().await()
        testScheduler.runCurrent()
        assertEquals(1, executor.unfinishedStrategyCalls)
        assertEquals(2, executor.takeUnfinishedStrategyPeak(), "Both calls were still running when this period began")
        assertEquals(1, executor.takeUnfinishedStrategyPeak())

        second.complete("decided")
        running.last().await()
    }

    /** 同一局正在計算而略過的請求不回報。 */
    @Test
    fun `a skipped request is not observed`() = runTest {
        var observed = 0
        val executor = AiDecisionExecutor(StandardTestDispatcher(testScheduler), capacity = 4, timeout = TIMEOUT, observer = { _, _, _ -> observed++ })
        val gate = CompletableDeferred<String>()
        val first = async { executor.decide(gameId, playerId, STRATEGY_KEY, decide = { gate.await() }, fallback = { "fallback" }) }
        testScheduler.runCurrent()

        assertNull(executor.decide(gameId, playerId, STRATEGY_KEY, decide = { "second" }, fallback = { "fallback" }))
        assertEquals(0, observed)
        gate.complete("first")
        first.await()
        assertEquals(1, observed)
    }

    /**
     * 使用虛擬時間的執行器與回報紀錄。
     *
     * @param scope 測試的作用域，提供虛擬時間。
     * @param capacity 名額。
     */
    private class Fixture(scope: TestScope, capacity: Int = 4) {
        val reporter = RecordingReporter()
        val executor = AiDecisionExecutor(StandardTestDispatcher(scope.testScheduler), capacity = capacity, timeout = TIMEOUT, reporter = reporter)

        /** 不理會取消、直到 [gate] 完成才返回的策略。 */
        suspend fun uncooperative(gate: CompletableDeferred<Unit>): String = withContext(NonCancellable) {
            gate.await()
            "late"
        }
    }

    /** 依序記錄回報的事件。 */
    private class RecordingReporter : AiDecisionReporter {
        val events = mutableListOf<String>()

        override fun onTimedOut(gameId: Uuid, playerId: Uuid, strategyKey: String?) {
            events += "timeout:$gameId"
        }

        override fun onPreviousStillRunning(gameId: Uuid, playerId: Uuid, strategyKey: String?) {
            events += "still-running:$gameId"
        }
    }

    /**
     * 記錄排程次數的調度器。
     *
     * @property delegate 實際執行的調度器。
     */
    private class RecordingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var dispatches = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches++
            delegate.dispatch(context, block)
        }
    }

    /** 收到的工作先保留，直到 [runAll]；[runEach] 為 true 後收到就立即執行。 */
    private class HeldDispatcher : CoroutineDispatcher() {
        private val held = ArrayDeque<Runnable>()

        /** 是否收到工作就立即執行。 */
        var runEach = false

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (runEach) block.run() else held.addLast(block)
        }

        /** 執行目前保留的所有工作。 */
        fun runAll() {
            while (held.isNotEmpty()) held.removeFirst().run()
        }
    }

    private companion object {
        const val STRATEGY_KEY = "test:strategy"
        val TIMEOUT = 5.seconds
    }
}
