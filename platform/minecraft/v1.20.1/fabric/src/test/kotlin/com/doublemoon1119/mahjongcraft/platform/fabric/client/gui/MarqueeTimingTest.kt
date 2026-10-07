package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.MarqueeTiming.END_PAUSE_MILLIS
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.MarqueeTiming.MILLIS_PER_PIXEL
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.MarqueeTiming.START_PAUSE_MILLIS
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證跑馬燈的停頓、移動與循環節奏。 */
class MarqueeTimingTest {
    private val overflow = 20
    private val travel = overflow * MILLIS_PER_PIXEL

    /** 沒有超出時永遠不移動。 */
    @Test
    fun `nothing moves without overflow`() {
        assertEquals(0, MarqueeTiming.offset(elapsedMillis = 10_000, overflow = 0))
    }

    /** 開頭停頓後移到底，停頓後移回開頭，再停頓並重新開始。 */
    @Test
    fun `pauses at both ends and repeats`() {
        assertEquals(0, MarqueeTiming.offset(START_PAUSE_MILLIS, overflow))
        assertEquals(overflow / 2, MarqueeTiming.offset(START_PAUSE_MILLIS + travel / 2, overflow))
        assertEquals(overflow, MarqueeTiming.offset(START_PAUSE_MILLIS + travel, overflow))
        assertEquals(overflow, MarqueeTiming.offset(START_PAUSE_MILLIS + travel + END_PAUSE_MILLIS - 1, overflow))
        assertEquals(overflow / 2, MarqueeTiming.offset(START_PAUSE_MILLIS + travel + END_PAUSE_MILLIS + travel / 2, overflow))
        assertEquals(0, MarqueeTiming.offset(START_PAUSE_MILLIS + travel * 2 + END_PAUSE_MILLIS, overflow))

        val cycle = travel * 2 + END_PAUSE_MILLIS * 2
        assertEquals(overflow / 2, MarqueeTiming.offset(START_PAUSE_MILLIS + cycle + travel / 2, overflow))
    }
}
