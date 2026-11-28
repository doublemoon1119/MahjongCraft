package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.service.DecisionTimerUpdate
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeDecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationBusyGate
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/** [GameDecisionAvailabilityService] 的呈現忙碌暫停與恢復測試。 */
class GameDecisionAvailabilityServiceTest {
    /** 驗證 busy 時停止既有 prompt，閒置後才從原剩餘時間恢復。 */
    @Test
    fun `test busy presentation stops and later resumes the same decision time`() = runTest {
        val repository = FakeGameRepository()
        val clock = MutableAvailabilityClock()
        val playerId = Uuid.random()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(
                    element = FakeMahjongPlayerFactory.create(
                        id = playerId,
                        hand = FakeHandFactory.create(lastDrawn = Tile.Honor.East),
                    ),
                ),
            ),
            flowConfig = GameFlowConfig(),
        )
        repository.setGame(game)
        val timerManager = GameDecisionTimerManager(
            gameRepository = repository,
            authorityResolver = GameDecisionAuthorityResolver(),
            timerFactory = PlayerDecisionTimerFactory(clock),
            clock = clock,
        )
        val publisher = FakeDecisionTimerUpdatePublisher()
        val busyGate = FakeGamePresentationBusyGate()
        val service = GameDecisionAvailabilityService(
            busyGate = busyGate,
            timerManager = timerManager,
            synchronizationService = DecisionTimerSynchronizationService(timerManager, repository, publisher),
        )
        assertTrue(service.reconcile(game.id))
        clock.now = 2_000.milliseconds
        busyGate.setBusy(game.id, true)

        assertFalse(service.reconcile(game.id))
        assertIs<DecisionTimerUpdate.Stopped>(publisher.updates.last().second)

        clock.now = 12_000.milliseconds
        busyGate.setBusy(game.id, false)
        assertTrue(service.reconcile(game.id))
        val active = assertIs<DecisionTimerUpdate.Active>(publisher.updates.last().second)
        assertEquals(3_000.milliseconds, active.baseRemaining)
    }

    /** 驗證所有真人離線時停止計時並累計暫停時間，有人回來後從原剩餘時間繼續，並回報目前是否暫停。 */
    @Test
    fun `test offline players pause the decision and accumulate the pause time`() = runTest {
        val repository = FakeGameRepository()
        val clock = MutableAvailabilityClock()
        val playerId = Uuid.random()
        val game = Game(
            tableState = FakeTableStateFactory.create(
                players = listOf(
                    element = FakeMahjongPlayerFactory.create(
                        id = playerId,
                        hand = FakeHandFactory.create(lastDrawn = Tile.Honor.East),
                    ),
                ),
            ),
            flowConfig = GameFlowConfig(),
        )
        repository.setGame(game)
        val timerManager = GameDecisionTimerManager(
            gameRepository = repository,
            authorityResolver = GameDecisionAuthorityResolver(),
            timerFactory = PlayerDecisionTimerFactory(clock),
            clock = clock,
        )
        val publisher = FakeDecisionTimerUpdatePublisher()
        var isOnline = true
        val pause = OfflineMatchPause(repository, { isOnline }, clock)
        val service = GameDecisionAvailabilityService(
            busyGate = FakeGamePresentationBusyGate(),
            timerManager = timerManager,
            synchronizationService = DecisionTimerSynchronizationService(timerManager, repository, publisher),
            offlinePause = pause,
        )
        assertTrue(service.reconcile(game.id))
        clock.now = 2_000.milliseconds
        isOnline = false

        assertFalse(service.reconcile(game.id))
        assertIs<DecisionTimerUpdate.Stopped>(publisher.updates.last().second)
        assertTrue(pause.isPaused(game.id))
        clock.now = 7_000.milliseconds
        assertFalse(service.reconcile(game.id))
        clock.now = 10_000.milliseconds
        assertFalse(service.reconcile(game.id))
        assertEquals(8_000.milliseconds, repository.getGame(game.id)?.offlinePauseElapsed)

        clock.now = 15_000.milliseconds
        isOnline = true
        assertTrue(service.reconcile(game.id))
        val active = assertIs<DecisionTimerUpdate.Active>(publisher.updates.last().second)
        assertEquals(3_000.milliseconds, active.baseRemaining)
        assertEquals(8_000.milliseconds, repository.getGame(game.id)?.offlinePauseElapsed)
        assertFalse(pause.isPaused(game.id))

        clock.now = 20_000.milliseconds
        isOnline = false
        assertFalse(service.reconcile(game.id))
        assertEquals(8_000.milliseconds, repository.getGame(game.id)?.offlinePauseElapsed)
    }

    /** 驗證只有 AI 的對局不因沒有真人在線而暫停。 */
    @Test
    fun `test offline pause never applies to a match without human players`() = runTest {
        val repository = FakeGameRepository()
        val ai = FakeMahjongPlayerFactory.create()
        val game = Game(
            tableState = FakeTableStateFactory.create(players = listOf(ai)),
            flowConfig = GameFlowConfig(),
            aiPlayerStrategyKeys = mapOf(ai.id to "test:ai"),
        )
        repository.setGame(game)
        val pause = OfflineMatchPause(repository, { false }, MutableAvailabilityClock())

        assertFalse(pause.update(game.id))
        assertFalse(pause.isPaused(game.id))
    }

    /** 驗證清除後不再回報暫停，第一次判斷只重新記下時刻，不把清除前後的間隔算進暫停時間。 */
    @Test
    fun `test offline pause restarts measuring after clear`() = runTest {
        val repository = FakeGameRepository()
        val clock = MutableAvailabilityClock()
        val game = Game(
            tableState = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create())),
            flowConfig = GameFlowConfig(),
        )
        repository.setGame(game)
        val pause = OfflineMatchPause(repository, { false }, clock)

        assertTrue(pause.update(game.id))
        pause.clear()
        assertFalse(pause.isPaused(game.id))
        clock.now = 30_000.milliseconds
        assertTrue(pause.update(game.id))
        assertEquals(Duration.ZERO, repository.getGame(game.id)?.offlinePauseElapsed)

        clock.now = 31_000.milliseconds
        assertTrue(pause.update(game.id))
        assertEquals(1_000.milliseconds, repository.getGame(game.id)?.offlinePauseElapsed)
    }

    /** 驗證同一張桌子的下一場不沿用上一場暫停中留下的時刻。 */
    @Test
    fun `test offline pause of a previous match does not count toward the next match`() = runTest {
        val repository = FakeGameRepository()
        val clock = MutableAvailabilityClock()
        val first = Game(
            tableState = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create())),
            flowConfig = GameFlowConfig(),
        )
        repository.setGame(first)
        val pause = OfflineMatchPause(repository, { false }, clock)
        assertTrue(pause.update(first.id))

        val next = first.copy(matchId = Uuid.random(), offlinePauseElapsed = Duration.ZERO)
        repository.setGame(next)
        clock.now = 600_000.milliseconds
        assertTrue(pause.update(next.id))
        assertEquals(Duration.ZERO, repository.getGame(next.id)?.offlinePauseElapsed)

        clock.now = 601_000.milliseconds
        assertTrue(pause.update(next.id))
        assertEquals(1_000.milliseconds, repository.getGame(next.id)?.offlinePauseElapsed)
    }
}

/** 可由 availability 測試直接推進的單調時間來源。 */
private class MutableAvailabilityClock : MonotonicClock {
    /** 目前回傳的單調時間毫秒數。 */
    var now: Duration = Duration.ZERO

    /** 回傳目前測試指定的單調時間。 */
    override fun now(): Duration = now
}
