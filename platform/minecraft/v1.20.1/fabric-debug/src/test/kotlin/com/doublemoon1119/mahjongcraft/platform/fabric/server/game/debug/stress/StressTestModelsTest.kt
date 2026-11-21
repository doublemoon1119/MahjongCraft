package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證壓力測試的爬坡計畫、安全閥與滾動統計，不依賴機器效能。 */
class StressTestModelsTest {
    /** 爬坡每經過一個間隔增加一批桌數。 */
    @Test
    fun `ramp adds a step of tables every interval`() {
        val plan = StressRampPlan(initialTables = 4, stepTables = 4, intervalTicks = 600)

        assertEquals(4, plan.tablesAt(0))
        assertEquals(4, plan.tablesAt(599))
        assertEquals(8, plan.tablesAt(600))
        assertEquals(16, plan.tablesAt(1_800))
    }

    /** 安全閥觸發時，最大可承受桌數是前一個階段；第一個階段就觸發時為 0。 */
    @Test
    fun `ramp reports the last fully sustained stage`() {
        val plan = StressRampPlan(initialTables = 4, stepTables = 4, intervalTicks = 600)

        assertEquals(0, plan.sustainedTablesAt(100))
        assertEquals(4, plan.sustainedTablesAt(700))
        assertEquals(12, plan.sustainedTablesAt(1_900))
    }

    /** 間隔在目標節奏上下正常抖動時不會停止，落後量也不會無限累積。 */
    @Test
    fun `normal interval jitter does not stop the test`() {
        val valve = StressSafetyValve(StressSafetyThresholds())
        val random = Random(SEED)

        repeat(20_000) {
            assertNull(valve.recordTick(45.0 + random.nextDouble() * 10.0, countStutter = true))
        }
    }

    /** 連續兩個不重疊視窗的平均間隔都超過門檻才停止；只有一個視窗變慢不停止。 */
    @Test
    fun `sustained slow windows stop the test`() {
        val thresholds = StressSafetyThresholds(windowTicks = 4, stutterTicks = 1, fallingBehindAverageMillis = 55.0, maxLagMillis = 1_000_000.0)
        val valve = StressSafetyValve(thresholds)

        repeat(4) { assertNull(valve.recordTick(60.0, countStutter = true)) }
        repeat(4) { assertNull(valve.recordTick(50.0, countStutter = true)) }
        repeat(4) { assertNull(valve.recordTick(60.0, countStutter = true)) }
        repeat(3) { assertNull(valve.recordTick(60.0, countStutter = true)) }
        assertEquals(StressStopReason.FALLING_BEHIND, valve.recordTick(60.0, countStutter = true))
    }

    /** 落後量在伺服器追上時下降，不會降到負值；進入新的爬坡階段時重設。 */
    @Test
    fun `lag recovers when the server catches up`() {
        val valve = StressSafetyValve(StressSafetyThresholds(maxLagMillis = 1_000_000.0))

        repeat(10) { valve.recordTick(150.0, countStutter = false) }
        assertEquals(1_000.0, valve.lagMillis, 1e-9)
        repeat(10) { valve.recordTick(10.0, countStutter = false) }
        assertEquals(600.0, valve.lagMillis, 1e-9)
        repeat(100) { valve.recordTick(10.0, countStutter = false) }
        assertEquals(0.0, valve.lagMillis, 1e-9)
        valve.recordTick(250.0, countStutter = false)
        valve.resetLag()
        assertEquals(0.0, valve.lagMillis, 1e-9)
    }

    /** 落後超過上限時不等視窗判定就緊急停止，搶在 watchdog 強制關閉伺服器之前。 */
    @Test
    fun `severe lag stops before the windows are judged`() {
        val valve = StressSafetyValve(StressSafetyThresholds(windowTicks = 200, maxLagMillis = 1_000.0))

        assertNull(valve.recordTick(550.0, countStutter = false))
        assertEquals(StressStopReason.FALLING_BEHIND, valve.recordTick(560.0, countStutter = false), "1010 ms of lag exceeds the 1000 ms cap.")
    }

    /** 暖機期間的 tick 不列入卡頓判斷，但仍計入是否落後。 */
    @Test
    fun `warm-up ticks do not count as stutter`() {
        val valve = StressSafetyValve(StressSafetyThresholds(stutterLimitMillis = 100.0, windowTicks = 4, stutterTicks = 1))

        valve.recordTick(150.0, countStutter = false)
        assertFalse(valve.stuttering)
        valve.recordTick(150.0, countStutter = true)
        assertTrue(valve.stuttering)
    }

    /** 間隔尖峰在最近一段時間內達門檻次數才算持續卡頓；偶發的單次尖峰不算，舊的卡頓離開視窗後解除。 */
    @Test
    fun `stutter needs repeated interval spikes within the window`() {
        val valve = StressSafetyValve(StressSafetyThresholds(stutterLimitMillis = 100.0, windowTicks = 4, stutterTicks = 2, maxLagMillis = 1_000_000.0))

        valve.recordTick(150.0, countStutter = true)
        assertFalse(valve.stuttering)
        valve.recordTick(50.0, countStutter = true)
        valve.recordTick(150.0, countStutter = true)
        assertTrue(valve.stuttering)
        valve.recordTick(50.0, countStutter = true)
        valve.recordTick(50.0, countStutter = true)
        assertFalse(valve.stuttering)
    }

    /** 待寫佇列積壓達到容量比例時停止，未達到時繼續。 */
    @Test
    fun `history backlog stops at the configured ratio`() {
        val valve = StressSafetyValve(StressSafetyThresholds(backlogRatio = 0.8))

        assertNull(valve.checkHistory(pendingEvents = 204, capacity = 256, lostSegments = 0, writerFailed = false))
        assertEquals(StressStopReason.HISTORY_BACKLOG, valve.checkHistory(pendingEvents = 205, capacity = 256, lostSegments = 0, writerFailed = false))
    }

    /** 歷史遺失優先於寫入失敗與積壓，寫入失敗優先於積壓。 */
    @Test
    fun `lost history and writer failure stop immediately`() {
        val valve = StressSafetyValve(StressSafetyThresholds())

        assertEquals(StressStopReason.HISTORY_LOST, valve.checkHistory(pendingEvents = 256, capacity = 256, lostSegments = 1, writerFailed = true))
        assertEquals(StressStopReason.HISTORY_WRITER_FAILED, valve.checkHistory(pendingEvents = 256, capacity = 256, lostSegments = 0, writerFailed = true))
    }

    /** 固定桌數必須介於 1 與上限之間。 */
    @Test
    fun `fixed table count is bounded`() {
        assertEquals(STRESS_MAX_TABLES, StressTestMode.Fixed(STRESS_MAX_TABLES).tables)
        assertFailsWith<IllegalArgumentException> { StressTestMode.Fixed(0) }
        assertFailsWith<IllegalArgumentException> { StressTestMode.Fixed(STRESS_MAX_TABLES + 1) }
    }

    /** 滾動統計只保留最近的數值，並以最接近的排名計算百分位數。 */
    @Test
    fun `rolling samples keep only the most recent values`() {
        val samples = RollingSamples(capacity = 4)
        listOf(100.0, 1.0, 2.0, 3.0, 4.0).forEach(samples::add)

        assertEquals(4, samples.size)
        assertEquals(4.0, samples.max())
        assertEquals(2.5, samples.average())
        assertEquals(4.0, samples.percentile(0.95))
        assertEquals(2.0, samples.percentile(0.5))
    }

    /** 沒有數值時統計值都是 0。 */
    @Test
    fun `empty samples report zero`() {
        val samples = RollingSamples(capacity = 4)

        assertEquals(0.0, samples.average())
        assertEquals(0.0, samples.max())
        assertEquals(0.0, samples.percentile(0.95))
    }

    /** 直方圖統計全部數值，分位數以落點那一格的上緣回報，不超過實際最大值。 */
    @Test
    fun `histogram reports percentiles of every value`() {
        val histogram = MillisHistogram(resolutionMillis = 1.0, limitMillis = 100.0)
        (1..100).forEach { histogram.add(it - 0.5) }

        assertEquals(100L, histogram.count)
        assertEquals(50.0, histogram.average(), 1e-9)
        assertEquals(99.5, histogram.max())
        assertEquals(95.0, histogram.percentile(0.95))
        assertEquals(99.0, histogram.percentile(0.99))
        assertEquals(99.5, histogram.percentile(1.0))
    }

    /** 超過上限的數值歸入最後一格，分位數落在那裡時回報實際最大值。 */
    @Test
    fun `histogram reports the real maximum for values beyond the limit`() {
        val histogram = MillisHistogram(resolutionMillis = 1.0, limitMillis = 10.0)
        repeat(98) { histogram.add(2.2) }
        histogram.add(400.0)
        histogram.add(7_000.0)

        assertEquals(3.0, histogram.percentile(0.95))
        assertEquals(400.0 + 7_000.0 + 98 * 2.2, histogram.average() * 100, 1e-6)
        assertEquals(7_000.0, histogram.percentile(0.99))
        assertEquals(0.0, MillisHistogram().percentile(0.99))
    }

    private companion object {
        /** 隨機間隔的固定種子。 */
        const val SEED = 20_261_009
    }
}
