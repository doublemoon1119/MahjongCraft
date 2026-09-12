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
                    FakeMahjongPlayerFactory.create(
                        id = playerId,
                        hand = FakeHandFactory.create(lastDrawn = Tile.Honor.East),
                    ),
                ),
            ),
            flowConfig = GameFlowConfig(),
        )
        repository.setGame(game)
        val timerManager = GameDecisionTimerManager(
            repository,
            GameDecisionAuthorityResolver(),
            PlayerDecisionTimerFactory(clock),
            clock,
        )
        val publisher = FakeDecisionTimerUpdatePublisher()
        val busyGate = FakeGamePresentationBusyGate()
        val service = GameDecisionAvailabilityService(
            busyGate,
            timerManager,
            DecisionTimerSynchronizationService(timerManager, repository, publisher),
        )
        assertTrue(service.reconcile(game.id))
        clock.nowMillis = 2_000L
        busyGate.setBusy(game.id, true)

        assertFalse(service.reconcile(game.id))
        assertIs<DecisionTimerUpdate.Stopped>(publisher.updates.last().second)

        clock.nowMillis = 12_000L
        busyGate.setBusy(game.id, false)
        assertTrue(service.reconcile(game.id))
        val active = assertIs<DecisionTimerUpdate.Active>(publisher.updates.last().second)
        assertEquals(3_000L, active.baseRemainingMillis)
    }
}

/** 可由 availability 測試直接推進的單調時間來源。 */
private class MutableAvailabilityClock : MonotonicClock {
    /** 目前回傳的單調時間毫秒數。 */
    var nowMillis: Long = 0L

    /** 回傳目前測試指定的單調時間。 */
    override fun nowMillis(): Long = nowMillis
}
