package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryWriterObserver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryWriterStage
import java.io.BufferedWriter
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.time.Duration

/**
 * 累計歷史背景工作各環節耗時與已處理事件數；背景執行緒寫入，伺服器主執行緒讀取。
 *
 * 環節統計可在暖機結束時 [resetStages]；已處理事件數只增不減，呼叫端自行記下基準值計算差額。
 */
class StressWriterTimer : HistoryWriterObserver {
    /** 保護所有欄位的鎖。 */
    private val lock = Any()

    /** 各環節的耗時統計。 */
    private val stages = mutableMapOf<HistoryWriterStage, TimingAccumulator>()

    /** 開始以來已寫入或編碼後確認移除的事件數。 */
    private var eventsWritten = 0L

    override fun onStage(stage: HistoryWriterStage, duration: Duration) = synchronized(lock) {
        stages.getOrPut(stage, ::TimingAccumulator).add(duration)
    }

    override fun onEventsWritten(count: Int) = synchronized(lock) {
        eventsWritten += count
    }

    /** 清除環節統計，不影響已處理事件數。 */
    fun resetStages() = synchronized(lock) {
        stages.clear()
    }

    /** 有資料的環節統計，依 [HistoryWriterStage] 宣告順序排列。 */
    fun stageSummaries(): Map<HistoryWriterStage, TimingSummary> = synchronized(lock) {
        HistoryWriterStage.entries.mapNotNull { stage -> stages[stage]?.let { stage to it.summary() } }.toMap()
    }

    /** 開始以來已處理的事件數。 */
    fun eventsWritten(): Long = synchronized(lock) { eventsWritten }
}

/**
 * JVM 記憶體回收的累計次數與耗時。
 *
 * @property count 回收次數。
 * @property millis JVM 回報的累計回收耗時毫秒數；以 G1 等分代回收器而言即為暫停時間。
 */
data class GcTotals(val count: Long, val millis: Long) {
    /** 與較早讀數 [other] 的差額。 */
    operator fun minus(other: GcTotals): GcTotals = GcTotals(count - other.count, millis - other.millis)

    companion object {
        /** 讀取目前所有回收器的累計值；回收器不提供數值時視為 0。 */
        fun read(): GcTotals = ManagementFactory.getGarbageCollectorMXBeans().fold(GcTotals(0, 0)) { totals, bean ->
            GcTotals(totals.count + bean.collectionCount.coerceAtLeast(0), totals.millis + bean.collectionTime.coerceAtLeast(0))
        }
    }
}

/**
 * 時間序列檔的一列：約一秒內的彙總。
 *
 * @property elapsedSeconds 開始後經過的實際秒數。
 * @property warmup 是否仍在暖機期間。
 * @property tables 同時進行的桌數。
 * @property ticks 這段期間的 tick 數。
 * @property tickAverageMillis 這段期間 tick 本身處理時間的平均毫秒數。
 * @property tickMaxMillis 這段期間 tick 本身處理時間的最大毫秒數。
 * @property tickIntervalAverageMillis 這段期間實際 tick 間隔的平均毫秒數。
 * @property tickIntervalMaxMillis 這段期間實際 tick 間隔的最大毫秒數。
 * @property lagMillis 這段期間結束時相對每 tick 50 ms 節奏的落後毫秒數。
 * @property advanceMillis 這段期間 MahjongCraft 推進在主執行緒上執行的總毫秒數。
 * @property steps 這段期間推進的步數。
 * @property stepAverageMillis 這段期間單步 MahjongCraft 推進耗時的平均毫秒數。
 * @property stageTotalMillis 這段期間各環節的總毫秒數。
 * @property historyRecordingMillis 這段期間歷史記錄的總毫秒數。
 * @property aiDecisions 這段期間有結果的 AI 決策數。
 * @property aiLatencyAverageMillis 這段期間 AI 決策延遲的平均毫秒數。
 * @property aiLatencyMaxMillis 這段期間 AI 決策延遲的最大毫秒數。
 * @property aiTimeouts 這段期間逾時而使用固定命令的 AI 決策數。
 * @property staleDecisions 這段期間過期而沒有套用的 AI 決策數。
 * @property strategyCallsPeak 這段期間同時存在、尚未真正結束的策略工作數的最大值，包含 tick 之間短暫出現的工作。
 * @property eventsProduced 這段期間新加入待寫佇列的歷史事件數。
 * @property eventsWritten 這段期間背景工作處理完的歷史事件數。
 * @property pendingEvents 這段期間結束時待寫佇列的事件數。
 * @property lostSegments 這段期間結束時出現序號缺口的場次數。
 * @property completedMatches 開始以來已打完的場數。
 * @property usedMemoryMiB 這段期間結束時 JVM 已使用的記憶體 MiB。
 * @property gc 這段期間的記憶體回收次數與耗時。
 */
data class StressTimeSeriesRow(
    val elapsedSeconds: Double,
    val warmup: Boolean,
    val tables: Int,
    val ticks: Int,
    val tickAverageMillis: Double,
    val tickMaxMillis: Double,
    val tickIntervalAverageMillis: Double,
    val tickIntervalMaxMillis: Double,
    val lagMillis: Double,
    val advanceMillis: Double,
    val steps: Int,
    val stepAverageMillis: Double,
    val stageTotalMillis: Map<StressStepStage, Double>,
    val historyRecordingMillis: Double,
    val aiDecisions: Int,
    val aiLatencyAverageMillis: Double,
    val aiLatencyMaxMillis: Double,
    val aiTimeouts: Int,
    val staleDecisions: Int,
    val strategyCallsPeak: Int,
    val eventsProduced: Int,
    val eventsWritten: Long,
    val pendingEvents: Int,
    val lostSegments: Int,
    val completedMatches: Int,
    val usedMemoryMiB: Long,
    val gc: GcTotals,
)

/**
 * 寫進存檔資料夾的每秒時間序列 CSV；每列寫入後立即 flush，伺服器中途結束也保留已寫的部分。
 *
 * @property path 檔案位置。
 * @property scenarioId 對局情境識別碼，寫在每一列。
 * @property pace 推進節奏，寫在每一列。
 * @property historyMode 歷史處理方式，寫在每一列。
 */
class StressTimeSeriesFile private constructor(
    val path: Path,
    private val scenarioId: String,
    private val pace: StressTestPace,
    private val historyMode: StressHistoryMode,
    private val writer: BufferedWriter,
) : AutoCloseable {
    /** 寫入一列。 */
    fun append(row: StressTimeSeriesRow) {
        writer.write(stressTimeSeriesCsvLine(scenarioId, pace, historyMode, row))
        writer.newLine()
        writer.flush()
    }

    override fun close() = writer.close()

    companion object {
        /**
         * 建立新檔並寫入標題列。
         *
         * @param path 檔案位置；上層資料夾不存在時一併建立。
         * @param scenarioId 對局情境識別碼。
         * @param pace 推進節奏。
         * @param historyMode 歷史處理方式。
         */
        fun create(
            path: Path,
            scenarioId: String,
            pace: StressTestPace,
            historyMode: StressHistoryMode,
        ): StressTimeSeriesFile {
            Files.createDirectories(path.parent)
            val writer = Files.newBufferedWriter(path)
            writer.write(STRESS_TIME_SERIES_HEADER)
            writer.newLine()
            writer.flush()
            return StressTimeSeriesFile(path, scenarioId, pace, historyMode, writer)
        }
    }
}

/** 時間序列 CSV 的標題列；各環節與歷史記錄欄位為這段期間的總毫秒數，環節欄位順序與 [StressStepStage] 相同。 */
internal val STRESS_TIME_SERIES_HEADER: String = listOf(
    "elapsedSeconds", "warmup", "scenario", "pace", "historyMode", "tables", "ticks", "tickAvgMs", "tickMaxMs", "tickIntervalAvgMs",
    "tickIntervalMaxMs", "lagMs", "advanceMs", "steps", "stepAvgMs",
    "aiContextMs", "rulesAndStateMs", "snapshotSyncMs", "aiDecisionMs", "historyRecordingMs", "aiDecisions", "aiLatencyAvgMs",
    "aiLatencyMaxMs", "aiTimeouts", "staleDecisions", "strategyCallsPeak", "eventsProduced", "eventsWritten", "pendingEvents",
    "lostSegments", "completedMatches", "usedMemoryMiB", "gcCount", "gcMs",
).joinToString(",")

/** 把一列時間序列格式化成 CSV，欄位順序與 [STRESS_TIME_SERIES_HEADER] 相同。 */
internal fun stressTimeSeriesCsvLine(
    scenarioId: String,
    pace: StressTestPace,
    historyMode: StressHistoryMode,
    row: StressTimeSeriesRow,
): String = with(row) {
    listOf(
        formatDecimal(elapsedSeconds),
        warmup.toString(),
        scenarioId,
        pace.commandName,
        historyMode.commandName,
        tables.toString(),
        ticks.toString(),
        formatDecimal(tickAverageMillis),
        formatDecimal(tickMaxMillis),
        formatDecimal(tickIntervalAverageMillis),
        formatDecimal(tickIntervalMaxMillis),
        formatDecimal(lagMillis),
        formatDecimal(advanceMillis),
        steps.toString(),
        formatDecimal(stepAverageMillis),
        *StressStepStage.entries.map { formatDecimal(stageTotalMillis[it] ?: 0.0) }.toTypedArray(),
        formatDecimal(historyRecordingMillis),
        aiDecisions.toString(),
        formatDecimal(aiLatencyAverageMillis),
        formatDecimal(aiLatencyMaxMillis),
        aiTimeouts.toString(),
        staleDecisions.toString(),
        strategyCallsPeak.toString(),
        eventsProduced.toString(),
        eventsWritten.toString(),
        pendingEvents.toString(),
        lostSegments.toString(),
        completedMatches.toString(),
        usedMemoryMiB.toString(),
        gc.count.toString(),
        gc.millis.toString(),
    ).joinToString(",")
}

/** 以兩位小數格式化。 */
private fun formatDecimal(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
