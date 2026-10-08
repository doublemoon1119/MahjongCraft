package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessFlowHistoryRuntime
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryScenario
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessStepTimer
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryWriterStage
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledHeadlessHistoryRegistries
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 驗證壓力測試輸出到 log 的報告與 AI 決策情境。 */
class StressTestChatFormattingTest {
    /** log 一行包含報告的所有欄位，毫秒數取一位小數，經過時間換算成秒，超時 tick 換算成比例。 */
    @Test
    fun `log line lists every report field`() {
        assertEquals(
            "reason=HISTORY_BACKLOG, scenario=mahjongcraft:riichi_east, mode=ramp, pace=fast, historyMode=encode, warmupSeconds=60, elapsedSeconds=120, " +
                "measuredSeconds=60.0, tables=8, completed=8, stalled=0, tickMs(avg/p95/max)=56.2/117.2/181.0, " +
                "slowTicks(>50ms/>100ms/>250ms)=25.0%/5.0%/0.0%, stutter(limitMs/tables)=100.0/24, stepMs(avg/p95/max)=7.2/29.3/127.1, " +
                "stepStageMs(avg/max)=ai_decision:4.0/90.0,rules_and_state:2.0/20.0,snapshot_sync:1.0/10.0,history_recording:0.5/7.0, " +
                "peakPerTick(steps/events)=8/30, events(produced/written)=600/540, eventsPerSecond(produced/written)=10.0/9.0, " +
                "writerStageMs(avg/max/count)=encode:1.5/4.0/9, pending(now/peak/capacity)=206/206/256, lostSegments=0, writerFailed=false, " +
                "memoryMiB=1024, gc(count/ms)=12/85, timeSeries=stress-test-20261008-120000-encode.csv, sustainedTables=4",
            stressTestReportLogLine(sampleReport()),
        )
    }

    /** 固定桌數模式在 log 中標出桌數。 */
    @Test
    fun `log line names the fixed table count`() {
        val report = sampleReport().copy(mode = StressTestMode.Fixed(12), pace = StressTestPace.REALTIME, historyMode = StressHistoryMode.WRITE)

        assertTrue("mode=fixed:12, pace=realtime, historyMode=write" in stressTestReportLogLine(report))
    }

    /** 尚未結束暖機時沒有列入統計的 tick，超時比例與事件速率都是 0。 */
    @Test
    fun `log line reports zero ratios before warm-up ends`() {
        val report = sampleReport().copy(measuredSeconds = 0.0, measuredTicks = 0, slowTicks = mapOf(50 to 0L, 100 to 0L, 250 to 0L))

        val line = stressTestReportLogLine(report)

        assertTrue("=0.0%/0.0%/0.0%" in line)
        assertTrue("eventsPerSecond(produced/written)=0.0/0.0" in line)
    }

    /** AI 決策的 log 一行列出決策階段與該 AI 自己看得到的手牌簡寫。 */
    @Test
    fun `AI decision log text lists the deciding player's hand`() = runBlocking {
        val timer = HeadlessStepTimer()
        val runtime = HeadlessFlowHistoryRuntime.create(
            scenario = HeadlessHistoryScenario.RIICHI_EAST,
            registries = bundledHeadlessHistoryRegistries(),
            store = AuthoritativeStateStore(historyRecordingEnabled = false),
            stepTimer = timer,
        )
        while (timer.slowestAiDecision == null) check(runtime.step()) { "The match made no progress before an AI decision." }

        val text = stressAiDecisionLogText(assertNotNull(timer.slowestAiDecision).context)

        assertTrue(text.startsWith("phase="), text)
        val hand = assertNotNull(Regex("hand=([^,]*)").find(text)).groupValues[1].split(" ")
        assertTrue(hand.size >= MIN_STANDING_TILES && hand.all { TILE_NOTATION.matches(it) }, text)
    }

    /** 測試用的完整報告。 */
    private fun sampleReport() = StressTestReport(
        scenarioId = "mahjongcraft:riichi_east",
        mode = StressTestMode.Ramp(StressRampPlan()),
        pace = StressTestPace.FAST,
        historyMode = StressHistoryMode.ENCODE,
        running = false,
        tables = 8,
        completedMatches = 8,
        failedMatches = 0,
        elapsedTicks = 2_400,
        warmupTicks = 1_200,
        warmupRemainingTicks = 0,
        measuredSeconds = 60.0,
        tickAverageMillis = 56.156,
        tickP95Millis = 117.154,
        tickMaxMillis = 180.974,
        stepAverageMillis = 7.24,
        stepP95Millis = 29.31,
        stepMaxMillis = 127.08,
        stepStages = mapOf(
            StressStepStage.AI_DECISION to TimingSummary(count = 100, averageMillis = 4.0, maxMillis = 90.0, totalMillis = 400.0),
            StressStepStage.RULES_AND_STATE to TimingSummary(count = 100, averageMillis = 2.0, maxMillis = 20.0, totalMillis = 200.0),
            StressStepStage.SNAPSHOT_SYNC to TimingSummary(count = 100, averageMillis = 1.0, maxMillis = 10.0, totalMillis = 100.0),
            StressStepStage.HISTORY_RECORDING to TimingSummary(count = 100, averageMillis = 0.5, maxMillis = 7.0, totalMillis = 50.0),
        ),
        writerStages = mapOf(HistoryWriterStage.ENCODE to TimingSummary(count = 9, averageMillis = 1.5, maxMillis = 4.0, totalMillis = 13.5)),
        eventsProduced = 600,
        eventsWritten = 540,
        maxStepsInTick = 8,
        maxEventsInTick = 30,
        measuredTicks = 1_200,
        slowTicks = mapOf(50 to 300L, 100 to 60L, 250 to 0L),
        gc = GcTotals(count = 12, millis = 85),
        stutterLimitMillis = 100.0,
        stutterTables = 24,
        pendingEvents = 206,
        pendingPeak = 206,
        pendingCapacity = 256,
        lostSegments = 0,
        writerFailed = false,
        usedMemoryMiB = 1_024,
        timeSeriesFile = "stress-test-20261008-120000-encode.csv",
        stopReason = StressStopReason.HISTORY_BACKLOG,
        sustainedTables = 4,
    )

    private companion object {
        /** 立牌最少的張數：四組副露後只剩一張。 */
        const val MIN_STANDING_TILES = 1

        /** 一張看得到的牌的簡寫：數牌、字牌，或以識別碼表示的規則擴充牌（例如赤寶牌）。 */
        val TILE_NOTATION = Regex("[1-9][mps]|[1-7]z|[a-z0-9_.-]+:[a-z0-9_./-]+")
    }
}
