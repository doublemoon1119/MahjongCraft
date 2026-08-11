package com.doublemoon1119.mahjongcraft.flow.client.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [ClientDecisionTimerStateStore] 的本地內插與凍結測試。 */
class ClientDecisionTimerStateStoreTest {
    /** 驗證基本時間耗盡後才從保留時間扣除本地內插值。 */
    @Test
    fun `test reading interpolates base before reserve`() {
        val clock = MutableClientClock()
        val store = ClientDecisionTimerStateStore(clock, staleAfterMillis = 1_500L)
        val gameId = Uuid.random()
        store.apply(gameId, PlayerDecisionPhase.OWN_TURN, 1_000L, 5_000L)
        clock.nowMillis = 1_200L

        val reading = store.reading()!!

        assertEquals(0L, reading.baseRemainingMillis)
        assertEquals(4_800L, reading.reserveRemainingMillis)
        assertFalse(reading.isSynchronizationStale)
    }

    /** 驗證超過同步門檻後凍結顯示，不繼續在客戶端宣告逾時。 */
    @Test
    fun `test stale synchronization freezes interpolation`() {
        val clock = MutableClientClock()
        val store = ClientDecisionTimerStateStore(clock, staleAfterMillis = 1_500L)
        store.apply(Uuid.random(), PlayerDecisionPhase.DISCARD_REACTION, 1_000L, 5_000L)
        clock.nowMillis = 3_000L

        val reading = store.reading()!!

        assertEquals(0L, reading.baseRemainingMillis)
        assertEquals(4_500L, reading.reserveRemainingMillis)
        assertTrue(reading.isSynchronizationStale)
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
