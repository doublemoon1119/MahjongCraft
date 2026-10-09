package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceManager
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [GameDecisionTimeoutService] 只認領逾時並請求推進，不等待推進完成。 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameDecisionTimeoutServiceTest {
    /** 逾時的對局被請求推進，處理在推進完成前就返回。 */
    @Test
    fun `timeouts request an advance without waiting for it`() = runTest {
        val repository = FakeGameRepository()
        val clock = ManualClock()
        val timers = GameDecisionTimerManager(repository, GameDecisionAuthorityResolver(), PlayerDecisionTimerFactory(clock), clock)
        val gate = CompletableDeferred<Unit>()
        val advanced = mutableListOf<Uuid>()
        val advances = AutomatedAdvanceManager(
            scope = backgroundScope,
            dispatcher = StandardTestDispatcher(testScheduler),
            advanceOnce = { gameId ->
                advanced += gameId
                gate.await()
                false
            },
            failureReporter = { _, error -> throw error },
        )
        val playerId = Uuid.random()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(FakeMahjongPlayerFactory.create(id = playerId)),
                pendingReaction = PendingReaction(discarderId = Uuid.random(), tileId = Uuid.random(), eligiblePlayerIds = setOf(playerId)),
            ),
            flowConfig = GameFlowConfig(),
        )
        repository.setGame(game)
        timers.reconcile(game.id)
        clock.nowMillis = 25_000L

        val scheduled = GameDecisionTimeoutService(timers, advances).processExpiredDecisions()

        assertEquals(setOf(game.id), scheduled)
        assertTrue(advances.isAdvancing(game.id), "The advance is requested")
        testScheduler.runCurrent()
        assertEquals(listOf(game.id), advanced)
        assertTrue(advances.isAdvancing(game.id), "Processing returned before the advance finished")
        gate.complete(Unit)
    }

    /** 可手動調整的單調時間來源。 */
    private class ManualClock : MonotonicClock {
        var nowMillis: Long = 0L

        override fun nowMillis(): Long = nowMillis
    }
}
