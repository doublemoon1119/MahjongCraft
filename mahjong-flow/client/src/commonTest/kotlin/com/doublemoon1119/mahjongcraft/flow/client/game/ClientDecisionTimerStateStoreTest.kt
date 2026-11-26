package com.doublemoon1119.mahjongcraft.flow.client.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/** [ClientDecisionTimerStateStore] 與剩餘時間推算的測試。 */
class ClientDecisionTimerStateStoreTest {
    /** 驗證同步時記錄收到的本地時間。 */
    @Test
    fun `test apply records the receive time`() {
        val clock = MutableClientClock().apply { now = 700.milliseconds }
        val store = ClientDecisionTimerStateStore(clock)

        store.apply(Uuid.random(), PlayerDecisionPhase.OWN_TURN, 1_000.milliseconds, 5_000.milliseconds)

        assertEquals(700.milliseconds, store.state?.receivedAt)
    }

    /** 驗證基本時間耗盡後才從保留時間扣除，兩者都不小於零。 */
    @Test
    fun `test remaining time consumes base before reserve`() {
        val state = ClientDecisionTimerState(Uuid.random(), PlayerDecisionPhase.OWN_TURN, 1_000.milliseconds, 5_000.milliseconds, 0.milliseconds)

        assertEquals(DecisionTimeRemaining(600.milliseconds, 5_000.milliseconds), state.remainingAfter(400.milliseconds))
        assertEquals(DecisionTimeRemaining(0.milliseconds, 4_800.milliseconds), state.remainingAfter(1_200.milliseconds))
        assertEquals(DecisionTimeRemaining(0.milliseconds, 0.milliseconds), state.remainingAfter(10_000.milliseconds))
        assertEquals(DecisionTimeRemaining(1_000.milliseconds, 5_000.milliseconds), state.remainingAfter((-50).milliseconds))
    }

    /** 剩餘時間是負數或無限長的同步一律拒絕，不覆寫目前狀態。 */
    @Test
    fun `test apply rejects negative or infinite remaining time`() {
        val store = ClientDecisionTimerStateStore(MutableClientClock())
        val gameId = Uuid.random()

        listOf((-1).milliseconds to 1.milliseconds, Duration.INFINITE to 1.milliseconds, 1.milliseconds to Duration.INFINITE).forEach { (base, reserve) ->
            assertFailsWith<IllegalArgumentException> { store.apply(gameId, PlayerDecisionPhase.OWN_TURN, base, reserve) }
        }
        assertNull(store.state)
    }

    /** 驗證停止事件只清除相同遊戲的有效計時。 */
    @Test
    fun `test stop ignores a different game`() {
        val store = ClientDecisionTimerStateStore(MutableClientClock())
        val activeGameId = Uuid.random()
        store.apply(activeGameId, PlayerDecisionPhase.OWN_TURN, 1_000.milliseconds, 1_000.milliseconds)

        store.stop(Uuid.random())
        assertEquals(activeGameId, store.state?.gameId)

        store.stop(activeGameId)
        assertNull(store.state)
    }
}

/** client timer store 測試使用的可控單調時間。 */
private class MutableClientClock : MonotonicClock {
    /** 目前測試時間。 */
    var now: Duration = Duration.ZERO

    /** 回傳目前測試時間。 */
    override fun now(): Duration = now
}
