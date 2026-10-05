package com.doublemoon1119.mahjongcraft.flow.client.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** [ClientDecisionTimerStateStore] 與剩餘時間推算的測試。 */
class ClientDecisionTimerStateStoreTest {
    /** 驗證同步時記錄收到的本地時間。 */
    @Test
    fun `test apply records the receive time`() {
        val clock = MutableClientClock().apply { nowMillis = 700L }
        val store = ClientDecisionTimerStateStore(clock)

        store.apply(Uuid.random(), PlayerDecisionPhase.OWN_TURN, 1_000L, 5_000L)

        assertEquals(700L, store.state?.receivedAtMillis)
    }

    /** 驗證基本時間耗盡後才從保留時間扣除，兩者都不小於零。 */
    @Test
    fun `test remaining time consumes base before reserve`() {
        val state = ClientDecisionTimerState(Uuid.random(), PlayerDecisionPhase.OWN_TURN, 1_000L, 5_000L, 0L)

        assertEquals(DecisionTimeRemaining(600L, 5_000L), state.remainingAfter(400L))
        assertEquals(DecisionTimeRemaining(0L, 4_800L), state.remainingAfter(1_200L))
        assertEquals(DecisionTimeRemaining(0L, 0L), state.remainingAfter(10_000L))
        assertEquals(DecisionTimeRemaining(1_000L, 5_000L), state.remainingAfter(-50L))
    }

    /** 驗證停止事件只清除相同遊戲的有效計時。 */
    @Test
    fun `test stop ignores a different game`() {
        val store = ClientDecisionTimerStateStore(MutableClientClock())
        val activeGameId = Uuid.random()
        store.apply(activeGameId, PlayerDecisionPhase.OWN_TURN, 1_000L, 1_000L)

        store.stop(Uuid.random())
        assertEquals(activeGameId, store.state?.gameId)

        store.stop(activeGameId)
        assertNull(store.state)
    }
}

/** client timer store 測試使用的可控單調時間。 */
private class MutableClientClock : MonotonicClock {
    /** 目前測試時間。 */
    var nowMillis: Long = 0L

    /** 回傳目前測試時間。 */
    override fun nowMillis(): Long = nowMillis
}
