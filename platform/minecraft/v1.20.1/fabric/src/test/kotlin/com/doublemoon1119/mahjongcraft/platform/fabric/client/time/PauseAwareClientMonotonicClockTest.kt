package com.doublemoon1119.mahjongcraft.platform.fabric.client.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** [PauseAwareClientMonotonicClock] 只在遊戲沒有暫停時前進的測試。 */
class PauseAwareClientMonotonicClockTest {
    /** 受測的時鐘。 */
    private val clock = PauseAwareClientMonotonicClock()

    /** 第一個 tick 只建立起點；之後每個未暫停的 tick 累加與上一個 tick 之間的時間。 */
    @Test
    fun `unpaused ticks accumulate elapsed time`() {
        clock.onTick(paused = false, now = millis(1_000))
        assertEquals(0.milliseconds, clock.now())

        clock.onTick(paused = false, now = millis(1_050))
        clock.onTick(paused = false, now = millis(1_100))

        assertEquals(100.milliseconds, clock.now())
    }

    /** 暫停期間不前進，恢復後也不把暫停的時間算進去。 */
    @Test
    fun `paused time is never counted`() {
        clock.onTick(paused = false, now = millis(0))
        clock.onTick(paused = false, now = millis(50))
        clock.onTick(paused = true, now = millis(30_000))
        clock.onTick(paused = true, now = millis(60_000))
        assertEquals(50.milliseconds, clock.now())

        clock.onTick(paused = false, now = millis(60_050))

        assertEquals(100.milliseconds, clock.now())
    }

    /**
     * 換算毫秒為奈秒。
     *
     * @param value 毫秒數。
     * @return 對應的奈秒數。
     */
    private fun millis(value: Long): Duration = value.milliseconds
}
