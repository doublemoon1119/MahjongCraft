package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import java.util.Locale

/** 時間序列 CSV 的標題列；各環節與歷史記錄欄位為這段期間的總毫秒數，環節欄位順序與 [StressStepStage] 相同。 */
internal val STRESS_TIME_SERIES_HEADER: String = listOf(
    "elapsedSeconds",
    "warmup",
    "scenario",
    "pace",
    "historyMode",
    "tables",
    "ticks",
    "tickAvgMs",
    "tickMaxMs",
    "tickIntervalAvgMs",
    "tickIntervalMaxMs",
    "lagMs",
    "advanceMs",
    "steps",
    "stepAvgMs",
    "aiContextMs",
    "rulesAndStateMs",
    "snapshotSyncMs",
    "aiDecisionMs",
    "historyRecordingMs",
    "aiDecisions",
    "aiLatencyAvgMs",
    "aiLatencyMaxMs",
    "aiTimeouts",
    "staleDecisions",
    "strategyCallsPeak",
    "eventsProduced",
    "eventsWritten",
    "pendingEvents",
    "lostSegments",
    "completedMatches",
    "usedMemoryMiB",
    "gcCount",
    "gcMs",
).joinToString(",")

/**
 * 把一列時間序列格式化成 CSV，欄位順序與 [STRESS_TIME_SERIES_HEADER] 相同。
 *
 * @param scenarioId 對局情境識別碼。
 * @param pace 每桌推進節奏。
 * @param historyMode 歷史處理方式。
 * @param row 要格式化的時間序列資料。
 * @return CSV 格式的一列文字。
 */
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

/**
 * 以兩位小數格式化。
 *
 * @param value 要格式化的數值。
 * @return 使用小數點格式化的文字。
 */
private fun formatDecimal(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
