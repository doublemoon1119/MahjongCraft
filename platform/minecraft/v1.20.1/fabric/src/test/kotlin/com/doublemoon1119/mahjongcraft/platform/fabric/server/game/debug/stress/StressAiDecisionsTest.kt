package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionExecutor
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionObserver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiDecisionOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** [StressAiDecisions] 讓各輪共用名額，並把決策結果只交給建立該對局的那一輪。 */
@OptIn(ExperimentalCoroutinesApi::class)
class StressAiDecisionsTest {
    private val playerId = Uuid.random()

    /** 上一輪停止後仍不理會取消的策略工作占著名額，下一輪不會因此多出新的名額。 */
    @Test
    fun `an uncooperative call left by the previous run still holds a shared permit`() = runTest {
        val decisions = decisions(this, capacity = 1)
        val previous = RecordingObserver()
        val next = RecordingObserver()
        val previousGame = Uuid.random()
        val gate = CompletableDeferred<Unit>()
        decisions.register(previousGame, previous)
        val waiting = async { decisions.executor.decide(previousGame, playerId, null, decide = { uncooperative(gate) }, fallback = { "fallback" }) }
        testScheduler.runCurrent()

        // 上一輪停止：取消登記並取消等待中的推進；策略工作不理會取消而繼續執行。
        decisions.unregister(previousGame, previous)
        waiting.cancel()
        testScheduler.runCurrent()
        val nextGame = Uuid.random()
        decisions.register(nextGame, next)

        assertEquals(1, decisions.executor.unfinishedStrategyCalls)
        assertEquals("fallback", decisions.executor.decide(nextGame, playerId, null, decide = { "decided" }, fallback = { "fallback" }))
        assertEquals(listOf(AiDecisionOutcome.TIMED_OUT), next.outcomes, "The next run waited for the permit held by the previous run")

        gate.complete(Unit)
        testScheduler.runCurrent()
        assertEquals("decided", decisions.executor.decide(nextGame, playerId, null, decide = { "decided" }, fallback = { "fallback" }))
        assertEquals(emptyList(), previous.outcomes)
    }

    /** 在上一輪開始、下一輪開始後才有結果的決策，不算進任何一輪；下一輪自己的決策照常算進下一輪。 */
    @Test
    fun `a decision started by the previous run is not counted by the next run`() = runTest {
        val decisions = decisions(this, capacity = 4)
        val previous = RecordingObserver()
        val next = RecordingObserver()
        val previousGame = Uuid.random()
        val gate = CompletableDeferred<String>()
        decisions.register(previousGame, previous)
        val late = async { decisions.executor.decide(previousGame, playerId, null, decide = { gate.await() }, fallback = { "fallback" }) }
        testScheduler.runCurrent()

        decisions.unregister(previousGame, previous)
        val nextGame = Uuid.random()
        decisions.register(nextGame, next)
        gate.complete("decided")
        assertEquals("decided", late.await())
        decisions.executor.decide(nextGame, playerId, null, decide = { "decided" }, fallback = { "fallback" })

        assertEquals(emptyList(), previous.outcomes)
        assertEquals(listOf(AiDecisionOutcome.DECIDED), next.outcomes)
    }

    /** 取消登記時登記的接收者已換成別的，不移除現有的登記。 */
    @Test
    fun `unregistering with another observer keeps the registration`() = runTest {
        val decisions = decisions(this, capacity = 4)
        val owner = RecordingObserver()
        val gameId = Uuid.random()
        decisions.register(gameId, owner)

        decisions.unregister(gameId, RecordingObserver())
        decisions.executor.decide(gameId, playerId, null, decide = { "decided" }, fallback = { "fallback" })

        assertEquals(listOf(AiDecisionOutcome.DECIDED), owner.outcomes)
    }

    /** 以虛擬時間執行策略的共用執行器。 */
    private fun decisions(scope: TestScope, capacity: Int) = StressAiDecisions { observer ->
        AiDecisionExecutor(
            dispatcher = StandardTestDispatcher(scope.testScheduler),
            capacity = capacity,
            timeout = TIMEOUT,
            observer = observer,
        )
    }

    /** 不理會取消、直到 [gate] 完成才返回的策略。 */
    private suspend fun uncooperative(gate: CompletableDeferred<Unit>): String = withContext(NonCancellable) {
        gate.await()
        "late"
    }

    /** 依序記錄收到的決策結果來源。 */
    private class RecordingObserver : AiDecisionObserver {
        val outcomes = mutableListOf<AiDecisionOutcome>()

        override fun onDecision(gameId: Uuid, outcome: AiDecisionOutcome, latency: Duration) {
            outcomes += outcome
        }
    }

    private companion object {
        /** 等待一次決策的上限。 */
        val TIMEOUT = 5.seconds
    }
}
