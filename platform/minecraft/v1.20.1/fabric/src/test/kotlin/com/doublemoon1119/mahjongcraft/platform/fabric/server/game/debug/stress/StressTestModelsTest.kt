package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

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

    /** 最近一段時間的平均耗時超過門檻才停止；推進集中在少數 tick 時，只看平均而不要求連續變慢。 */
    @Test
    fun `falling behind is judged by the recent average`() {
        val valve = StressSafetyValve(StressSafetyThresholds(windowTicks = 4, stutterTicks = 1, fallingBehindAverageMillis = 50.0))

        assertNull(valve.recordTick(300.0, countStutter = true), "A window that is not yet full must not stop the test.")
        assertNull(valve.recordTick(1.0, countStutter = true))
        assertNull(valve.recordTick(1.0, countStutter = true))
        assertEquals(StressStopReason.FALLING_BEHIND, valve.recordTick(1.0, countStutter = true))
        assertNull(valve.recordTick(1.0, countStutter = true), "The heavy tick has left the window.")
    }

    /** 嚴重過載時不等視窗填滿：累計落後超過上限就停止，搶在 watchdog 強制關閉伺服器之前。 */
    @Test
    fun `severe lag stops before the window fills`() {
        val valve = StressSafetyValve(StressSafetyThresholds(windowTicks = 200, fallingBehindAverageMillis = 50.0, maxLagMillis = 1_000.0))

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

    /** 最近一段時間內卡頓次數達門檻才算持續卡頓；偶發的單次尖峰不算，舊的卡頓離開視窗後解除。 */
    @Test
    fun `stutter needs repeated spikes within the window`() {
        val valve = StressSafetyValve(StressSafetyThresholds(stutterLimitMillis = 100.0, windowTicks = 4, stutterTicks = 2))

        valve.recordTick(150.0, countStutter = true)
        assertFalse(valve.stuttering)
        valve.recordTick(10.0, countStutter = true)
        valve.recordTick(150.0, countStutter = true)
        assertTrue(valve.stuttering)
        valve.recordTick(10.0, countStutter = true)
        valve.recordTick(10.0, countStutter = true)
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

    /** 每桌推進都對齊在週期的倍數上；每 tick 推進時任何 tick 都可以。 */
    @Test
    fun `steps align to the pace interval`() {
        assertEquals(20, alignedStepTick(1, 20))
        assertEquals(20, alignedStepTick(20, 20))
        assertEquals(40, alignedStepTick(21, 20))
        assertEquals(5, alignedStepTick(5, 1))
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
}
