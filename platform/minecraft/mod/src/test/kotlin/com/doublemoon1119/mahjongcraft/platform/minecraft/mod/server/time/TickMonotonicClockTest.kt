package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.time

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** 驗證 [TickMonotonicClock] 以 tick 計時與執行延遲工作。 */
class TickMonotonicClockTest {
    /** 每個 tick 前進 50 毫秒，沒有 tick 時時間不動。 */
    @Test
    fun `time advances by fifty milliseconds per tick`() {
        val clock = TickMonotonicClock()
        assertEquals(Duration.ZERO, clock.now())

        repeat(3) { clock.advanceTick() }

        assertEquals(150.milliseconds, clock.now())
    }

    /** 延遲換算成 tick 時無條件進位，到期的那個 tick 才執行。 */
    @Test
    fun `scheduled work runs on the tick its delay rounds up to`() {
        val clock = TickMonotonicClock()
        val runs = mutableListOf<Duration>()
        clock.scheduleAfter(delay = 60.milliseconds) { runs += clock.now() }

        clock.advanceTick()
        assertEquals(emptyList(), runs)
        clock.advanceTick()

        assertEquals(listOf(100.milliseconds), runs)
    }

    /** 取消的工作到期時不執行。 */
    @Test
    fun `cancelled work does not run`() {
        val clock = TickMonotonicClock()
        var ran = false
        val handle = clock.scheduleAfter(delay = 50.milliseconds) { ran = true }

        handle.dispose()
        clock.advanceTick()

        assertEquals(false, ran)
    }
}
