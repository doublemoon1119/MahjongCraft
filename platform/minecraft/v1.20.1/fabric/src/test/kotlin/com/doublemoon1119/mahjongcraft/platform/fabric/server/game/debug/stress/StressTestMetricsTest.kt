package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryWriterStage
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/** 驗證壓力測試的耗時累計、背景工作計時與時間序列 CSV。 */
class StressTestMetricsTest {
    /** 累計器回報筆數、平均、最大與總毫秒數；沒有資料時全為 0。 */
    @Test
    fun `timing accumulator summarizes durations`() {
        val accumulator = TimingAccumulator()
        assertEquals(TimingSummary.EMPTY, accumulator.summary())

        listOf(2, 8, 5).forEach { accumulator.add(it.milliseconds) }

        assertEquals(TimingSummary(count = 3, averageMillis = 5.0, maxMillis = 8.0, totalMillis = 15.0), accumulator.summary())
    }

    /** 暖機結束清除環節統計時，已處理事件數不歸零，讓呼叫端以基準值計算差額。 */
    @Test
    fun `resetting writer stages keeps the processed event count`() {
        val timer = StressWriterTimer()
        timer.onStage(HistoryWriterStage.BATCH_WRITE, 4.milliseconds)
        timer.onStage(HistoryWriterStage.DISK_USAGE, 1.milliseconds)
        timer.onEventsWritten(64)

        timer.resetStages()
        timer.onStage(HistoryWriterStage.ENCODE, 2.milliseconds)

        assertEquals(listOf(HistoryWriterStage.ENCODE), timer.stageSummaries().keys.toList())
        assertEquals(64, timer.eventsWritten())
    }

    /** 環節統計依宣告順序列出，與加入順序無關。 */
    @Test
    fun `writer stages are listed in declaration order`() {
        val timer = StressWriterTimer()
        timer.onStage(HistoryWriterStage.ARCHIVE, 1.milliseconds)
        timer.onStage(HistoryWriterStage.DISK_USAGE, 1.milliseconds)

        assertEquals(listOf(HistoryWriterStage.DISK_USAGE, HistoryWriterStage.ARCHIVE), timer.stageSummaries().keys.toList())
    }

    /** 記憶體回收累計值相減得到期間的差額。 */
    @Test
    fun `gc totals subtract to the period difference`() {
        assertEquals(GcTotals(count = 3, millis = 40), GcTotals(count = 10, millis = 100) - GcTotals(count = 7, millis = 60))
    }

    /** 時間序列檔先寫標題列，每列欄位數與標題相同，並帶有情境、節奏與歷史處理方式。 */
    @Test
    fun `time series file writes a header and matching rows`() {
        val path = createTempDirectory("mahjongcraft-stress-series-").resolve("nested").resolve("series.csv")
        StressTimeSeriesFile.create(path, "mahjongcraft:riichi_east", StressTestPace.REALTIME, StressHistoryMode.OFF).use { file ->
            file.append(sampleRow())
        }

        val lines = path.readLines()

        assertEquals(listOf(STRESS_TIME_SERIES_HEADER, EXPECTED_ROW), lines)
        assertEquals(lines[0].split(",").size, lines[1].split(",").size)
    }

    /** 測試用的一列時間序列。 */
    private fun sampleRow() = StressTimeSeriesRow(
        elapsedSeconds = 61.5,
        warmup = false,
        tables = 12,
        ticks = 20,
        tickAverageMillis = 31.25,
        tickMaxMillis = 120.0,
        steps = 12,
        stepAverageMillis = 2.5,
        stageTotalMillis = mapOf(
            StressStepStage.AI_DECISION to 18.0,
            StressStepStage.RULES_AND_STATE to 9.0,
            StressStepStage.SNAPSHOT_SYNC to 2.0,
            StressStepStage.HISTORY_RECORDING to 1.0,
        ),
        eventsProduced = 40,
        eventsWritten = 38,
        pendingEvents = 2,
        lostSegments = 0,
        completedMatches = 3,
        usedMemoryMiB = 900,
        gc = GcTotals(count = 1, millis = 7),
    )

    private companion object {
        /** [sampleRow] 對應的 CSV 列。 */
        const val EXPECTED_ROW = "61.50,false,mahjongcraft:riichi_east,realtime,off,12,20,31.25,120.00,12,2.50,18.00,9.00,2.00,1.00,40,38,2,0,3,900,1,7"
    }
}
