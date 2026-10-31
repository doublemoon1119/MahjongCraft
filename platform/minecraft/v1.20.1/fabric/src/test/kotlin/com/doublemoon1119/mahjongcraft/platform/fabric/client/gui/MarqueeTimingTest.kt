package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.MarqueeTiming.END_PAUSE
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.MarqueeTiming.START_PAUSE
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.MarqueeTiming.TIME_PER_PIXEL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** 驗證跑馬燈的停頓、移動與循環節奏。 */
class MarqueeTimingTest {
    /** 測試文字超出可見區的像素數。 */
    private val overflow = 20

    /** 單程移動所需時間。 */
    private val travel = TIME_PER_PIXEL * overflow

    /** 沒有超出時永遠不移動。 */
    @Test
    fun `nothing moves without overflow`() {
        assertEquals(0, MarqueeTiming.offset(elapsed = 10.seconds, overflow = 0))
    }

    /** 開頭停頓後移到底，停頓後移回開頭，再停頓並重新開始。 */
    @Test
    fun `pauses at both ends and repeats`() {
        assertEquals(0, MarqueeTiming.offset(START_PAUSE, overflow))
        assertEquals(overflow / 2, MarqueeTiming.offset(START_PAUSE + travel / 2, overflow))
        assertEquals(overflow, MarqueeTiming.offset(START_PAUSE + travel, overflow))
        assertEquals(overflow, MarqueeTiming.offset(START_PAUSE + travel + END_PAUSE - 1.milliseconds, overflow))
        assertEquals(overflow / 2, MarqueeTiming.offset(START_PAUSE + travel + END_PAUSE + travel / 2, overflow))
        assertEquals(0, MarqueeTiming.offset(START_PAUSE + travel * 2 + END_PAUSE, overflow))

        val cycle = travel * 2 + END_PAUSE * 2
        assertEquals(overflow / 2, MarqueeTiming.offset(START_PAUSE + cycle + travel / 2, overflow))
    }
}
