package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [DecisionTimerDisplay] 的本地倒數與凍結測試。 */
class DecisionTimerDisplayTest {
    /** 驗證同步後依本地時間倒數，先扣基本時間再扣保留時間。 */
    @Test
    fun `test reading counts down from the last synchronization`() {
        val clock = MutableClock()
        val store = ClientDecisionTimerStateStore(clock)
        val display = DecisionTimerDisplay(store, clock)
        store.apply(Uuid.random(), PlayerDecisionPhase.OWN_TURN, 1_000L, 5_000L)
        clock.nowMillis = 1_200L

        val reading = display.reading()!!

        assertEquals(0L, reading.baseRemainingMillis)
        assertEquals(4_800L, reading.reserveRemainingMillis)
        assertFalse(reading.isSynchronizationStale)
    }

    /** 驗證超過同步門檻後凍結顯示，不繼續在客戶端倒數到逾時。 */
    @Test
    fun `test stale synchronization freezes the countdown`() {
        val clock = MutableClock()
        val store = ClientDecisionTimerStateStore(clock)
        val display = DecisionTimerDisplay(store, clock)
        store.apply(Uuid.random(), PlayerDecisionPhase.DISCARD_REACTION, 1_000L, 5_000L)
        clock.nowMillis = 3_000L

        val reading = display.reading()!!

        assertEquals(0L, reading.baseRemainingMillis)
        assertEquals(6_000L - DecisionTimerDisplay.STALE_AFTER_MILLIS, reading.reserveRemainingMillis)
        assertTrue(reading.isSynchronizationStale)
    }

    /** 驗證沒有有效計時時不顯示倒數。 */
    @Test
    fun `test no reading without a synchronized timer`() {
        val clock = MutableClock()
        assertNull(DecisionTimerDisplay(ClientDecisionTimerStateStore(clock), clock).reading())
    }

    /** 可控的單調時間。 */
    private class MutableClock : MonotonicClock {
        /** 目前測試時間。 */
        var nowMillis: Long = 0L

        override fun nowMillis(): Long = nowMillis
    }
}
