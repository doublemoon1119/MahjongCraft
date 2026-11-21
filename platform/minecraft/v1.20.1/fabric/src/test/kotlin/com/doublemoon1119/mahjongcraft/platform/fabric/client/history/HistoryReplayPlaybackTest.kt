package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** 驗證回放的計時、速度、停頓加倍、到底停下與列表跟隨。 */
class HistoryReplayPlaybackTest {
    /** 一倍速一步停一秒，時間未到或暫停時不前進。 */
    @Test
    fun `steps advance after the dwell time while playing`() {
        val playback = HistoryReplayPlayback()

        assertNull(playback.advance(step = 0, stepCount = STEP_COUNT, now = 1.seconds, nextReady = true, marked = false))
        assertEquals(0, playback.play(step = 0, stepCount = STEP_COUNT, now = Duration.ZERO))
        assertNull(playback.advance(step = 0, stepCount = STEP_COUNT, now = 999.milliseconds, nextReady = true, marked = false))
        assertEquals(1, playback.advance(step = 0, stepCount = STEP_COUNT, now = 1.seconds, nextReady = true, marked = false))
        playback.pause()
        assertNull(playback.advance(step = 1, stepCount = STEP_COUNT, now = 5.seconds, nextReady = true, marked = false))
    }

    /** 有標記的交易停兩倍時間，速度倍數縮短停留時間。 */
    @Test
    fun `marked steps dwell twice as long and speed scales the dwell`() {
        val playback = HistoryReplayPlayback()
        playback.speed = HistoryReplaySpeed.QUADRUPLE

        assertEquals(250.milliseconds, playback.dwell(marked = false))
        assertEquals(500.milliseconds, playback.dwell(marked = true))
        playback.speed = HistoryReplaySpeed.HALF
        assertEquals(4.seconds, playback.dwell(marked = true))
    }

    /** 下一步牌面還沒到時原地等，到了就前進，新的一步重新計時。 */
    @Test
    fun `playback waits for the next state without skipping`() {
        val playback = HistoryReplayPlayback()
        playback.play(step = 0, stepCount = STEP_COUNT, now = Duration.ZERO)

        assertNull(playback.advance(step = 0, stepCount = STEP_COUNT, now = 3.seconds, nextReady = false, marked = false))
        assertEquals(1, playback.advance(step = 0, stepCount = STEP_COUNT, now = 4.seconds, nextReady = true, marked = false))
        assertNull(playback.advance(step = 1, stepCount = STEP_COUNT, now = 4.5.seconds, nextReady = true, marked = false))
    }

    /** 到最後一步自動暫停；在最後一步按播放從頭開始。 */
    @Test
    fun `playback stops at the last step and restarts from the beginning`() {
        val playback = HistoryReplayPlayback()
        playback.play(step = STEP_COUNT - 2, stepCount = STEP_COUNT, now = Duration.ZERO)

        assertEquals(STEP_COUNT - 1, playback.advance(step = STEP_COUNT - 2, stepCount = STEP_COUNT, now = 1.seconds, nextReady = true, marked = false))
        assertFalse(playback.playing)
        assertEquals(0, playback.play(step = STEP_COUNT - 1, stepCount = STEP_COUNT, now = 2.seconds))
        assertTrue(playback.playing)
    }

    /** 換步重新計時；捲動列表停止跟隨，播放、換步或換局恢復跟隨，換局會暫停。 */
    @Test
    fun `moves restart the timer and restore list following`() {
        val playback = HistoryReplayPlayback()
        playback.play(step = 0, stepCount = STEP_COUNT, now = Duration.ZERO)
        playback.scrolled()
        assertFalse(playback.following)

        playback.moved(now = 900.milliseconds)
        assertTrue(playback.following)
        assertNull(playback.advance(step = 3, stepCount = STEP_COUNT, now = 1.5.seconds, nextReady = true, marked = false))
        assertEquals(4, playback.advance(step = 3, stepCount = STEP_COUNT, now = 1.9.seconds, nextReady = true, marked = false))

        playback.scrolled()
        playback.roundChanged()
        assertTrue(playback.following)
        assertFalse(playback.playing)
    }

    /** 牌還在動畫中時停留不計時，動畫播完才開始算這一步停多久。 */
    @Test
    fun `holding restarts the dwell until the animation ends`() {
        val playback = HistoryReplayPlayback()
        playback.play(step = 0, stepCount = STEP_COUNT, now = Duration.ZERO)
        playback.hold(now = 2.seconds)

        assertNull(playback.advance(step = 0, stepCount = STEP_COUNT, now = 2.5.seconds, nextReady = true, marked = false))
        assertEquals(1, playback.advance(step = 0, stepCount = STEP_COUNT, now = 3.seconds, nextReady = true, marked = false))
    }

    /** 測試共用的總步數。 */
    private companion object {
        /** 測試共用的總步數。 */
        const val STEP_COUNT = 10
    }
}
