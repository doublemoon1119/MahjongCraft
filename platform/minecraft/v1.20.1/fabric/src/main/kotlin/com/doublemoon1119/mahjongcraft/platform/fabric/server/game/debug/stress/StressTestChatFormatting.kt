package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryWriterStage
import com.doublemoon1119.mahjongcraft.platform.fabric.text.prefixedConfigMessage
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.StressDebugKeys
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.Locale

/** 壓力測試的完整報告訊息：執行中顯示目前數據，停止後加上停止原因與爬坡結果。 */
internal fun stressTestReportMessage(report: StressTestReport): MutableText = prefixedConfigMessage(
    stressText(if (report.running) StressDebugKeys.TITLE_RUNNING else StressDebugKeys.TITLE_STOPPED),
    if (report.running) Formatting.AQUA else Formatting.YELLOW,
).also { message ->
    message.append(entry(StressDebugKeys.SCENARIO, Text.literal(report.scenarioId), Formatting.GRAY))
        .append(entry(StressDebugKeys.MODE, modeText(report.mode), Formatting.GRAY))
        .append(entry(StressDebugKeys.PACE, stressText(paceKey(report.pace)), Formatting.GRAY))
        .append(entry(StressDebugKeys.HISTORY_MODE, stressText(StressDebugKeys.HISTORY_MODE_PREFIX + report.historyMode.commandName), Formatting.GRAY))
        .append(entry(StressDebugKeys.TABLES, Text.literal(report.tables.toString()), Formatting.GREEN))
        .append(entry(StressDebugKeys.COMPLETED, Text.literal(report.completedMatches.toString()), Formatting.GREEN))
        .append(entry(StressDebugKeys.FAILED, Text.literal(report.failedMatches.toString()), if (report.failedMatches > 0) Formatting.RED else Formatting.GREEN))
        .append(entry(StressDebugKeys.ELAPSED, Text.literal(formatSeconds(report.elapsedTicks)), Formatting.GRAY))
        .append(entry(StressDebugKeys.WARMUP, warmupText(report), Formatting.GRAY))
        .append(entry(StressDebugKeys.TICK, Text.literal(formatTriple(report.tickAverageMillis, report.tickP95Millis, report.tickMaxMillis)), Formatting.AQUA))
        .append(entry(StressDebugKeys.SLOW_TICKS, Text.literal(formatSlowTicks(report)), Formatting.AQUA))
        .append(stutterEntry(report))
        .append(entry(StressDebugKeys.STEP, Text.literal(formatTriple(report.stepAverageMillis, report.stepP95Millis, report.stepMaxMillis)), Formatting.AQUA))
        .append(entry(StressDebugKeys.STEP_BREAKDOWN, Text.empty(), Formatting.AQUA))
    report.stepStages.forEach { (stage, summary) ->
        message.append(subEntry(stressText(StressDebugKeys.STEP_STAGE_PREFIX + stage.name.lowercase(Locale.ROOT)), formatAverageMax(summary)))
    }
    message.append(entry(StressDebugKeys.PEAK_PER_TICK, Text.literal("${report.maxStepsInTick} / ${report.maxEventsInTick}"), Formatting.AQUA))
        .append(entry(StressDebugKeys.EVENTS, Text.literal(formatEvents(report)), Formatting.AQUA))
        .append(entry(StressDebugKeys.WRITER_BREAKDOWN, Text.empty(), Formatting.AQUA))
    report.writerStages.forEach { (stage, summary) ->
        message.append(subEntry(stressText(writerStageKey(stage)), "${formatAverageMax(summary)} ×${summary.count}"))
    }
    message.append(entry(StressDebugKeys.PENDING, Text.literal("${report.pendingEvents} / ${report.pendingPeak} / ${report.pendingCapacity}"), Formatting.AQUA))
        .append(entry(StressDebugKeys.LOST, Text.literal(report.lostSegments.toString()), if (report.lostSegments > 0) Formatting.RED else Formatting.GREEN))
        .append(
            entry(
                StressDebugKeys.WRITER,
                stressText(if (report.writerFailed) StressDebugKeys.WRITER_FAILED else StressDebugKeys.WRITER_OK),
                if (report.writerFailed) Formatting.RED else Formatting.GREEN,
            ),
        )
        .append(entry(StressDebugKeys.MEMORY, Text.literal("${report.usedMemoryMiB} MiB"), Formatting.GRAY))
        .append(entry(StressDebugKeys.GC, Text.literal("${report.gc.count} / ${report.gc.millis} ms"), Formatting.GRAY))
    report.timeSeriesFile?.let { file -> message.append(entry(StressDebugKeys.TIME_SERIES, Text.literal(file), Formatting.GRAY)) }
    report.stopReason?.let { reason ->
        message.append(entry(StressDebugKeys.STOP_REASON, stressText(StressDebugKeys.REASON_PREFIX + reason.name.lowercase(Locale.ROOT)), Formatting.YELLOW))
    }
    report.sustainedTables?.let { tables ->
        message.append(entry(StressDebugKeys.SUSTAINED, Text.literal(tables.toString()), Formatting.GOLD))
    }
}

/** 報告的所有欄位，以一行英文輸出到 log；主控台開始的測試沒有玩家可收報告，也方便比較不同次的結果。 */
internal fun stressTestReportLogLine(report: StressTestReport): String = with(report) {
    buildString {
        append("reason=").append(stopReason)
        append(", scenario=").append(scenarioId)
        append(", mode=").append(
            when (mode) {
                is StressTestMode.Fixed -> "fixed:${mode.tables}"
                is StressTestMode.Ramp -> "ramp"
            },
        )
        append(", pace=").append(pace.commandName)
        append(", historyMode=").append(historyMode.commandName)
        append(", elapsedSeconds=").append(elapsedTicks / TICKS_PER_SECOND)
        append(", measuredSeconds=").append(formatMillis(measuredSeconds))
        append(", tables=").append(tables)
        append(", completed=").append(completedMatches)
        append(", stalled=").append(failedMatches)
        append(", tickMs(avg/p95/max)=").append(listOf(tickAverageMillis, tickP95Millis, tickMaxMillis).joinToString("/", transform = ::formatMillis))
        append(", slowTicks(").append(slowTicks.keys.joinToString("/") { ">${it}ms" }).append(")=").append(formatSlowTicks(report).replace(" ", ""))
        append(", stutter(limitMs/tables)=").append(formatMillis(stutterLimitMillis)).append('/').append(stutterTables)
        append(", stepMs(avg/p95/max)=").append(listOf(stepAverageMillis, stepP95Millis, stepMaxMillis).joinToString("/", transform = ::formatMillis))
        append(", stepStageMs(avg/max)=").append(
            stepStages.entries.joinToString(",") { (stage, summary) -> "${stage.name.lowercase(Locale.ROOT)}:${formatMillis(summary.averageMillis)}/${formatMillis(summary.maxMillis)}" },
        )
        append(", peakPerTick(steps/events)=").append("$maxStepsInTick/$maxEventsInTick")
        append(", events(produced/written)=").append("$eventsProduced/$eventsWritten")
        append(", eventsPerSecond(produced/written)=").append("${formatMillis(perSecond(eventsProduced, measuredSeconds))}/${formatMillis(perSecond(eventsWritten, measuredSeconds))}")
        append(", writerStageMs(avg/max/count)=").append(
            writerStages.entries.joinToString(",") { (stage, summary) ->
                "${stage.name.lowercase(Locale.ROOT)}:${formatMillis(summary.averageMillis)}/${formatMillis(summary.maxMillis)}/${summary.count}"
            },
        )
        append(", pending(now/peak/capacity)=").append("$pendingEvents/$pendingPeak/$pendingCapacity")
        append(", lostSegments=").append(lostSegments)
        append(", writerFailed=").append(writerFailed)
        append(", memoryMiB=").append(usedMemoryMiB)
        append(", gc(count/ms)=").append("${gc.count}/${gc.millis}")
        append(", timeSeries=").append(timeSeriesFile)
        append(", sustainedTables=").append(sustainedTables)
    }
}

/**
 * 一次 AI 出牌決策的情境，以一行英文輸出到 log，用來重現異常緩慢的決策。
 *
 * 牌以數字加花色表示：m 萬子、p 筒子、s 條子；字牌 1z 至 7z 依序為東、南、西、北、白、發、中；看不到的牌為 ?。
 */
internal fun stressAiDecisionLogText(context: AiDecisionContext): String {
    val snapshot = context.snapshot
    val self = snapshot.players.firstOrNull { it.id == context.selfId }
    return buildString {
        append("phase=").append(context.phase)
        append(", round=").append(snapshot.prevalentWind).append(' ').append(snapshot.roundNumber).append('-').append(snapshot.comboCount)
        append(", seatWind=").append(self?.seatWind)
        append(", hand=").append(self?.hand?.standingTiles?.joinToString(" ") { tileNotation(it.tile) })
        append(", drawn=").append(self?.hand?.lastDrawn?.let { tileNotation(it.tile) })
        append(", melds=").append(self?.hand?.melds?.joinToString(prefix = "[", postfix = "]") { meld -> "${meld.type} ${meld.tiles.joinToString(" ") { tileNotation(it.tile) }}" })
        append(", legalActions=").append(context.legalActions.joinToString(prefix = "[", postfix = "]") { it::class.simpleName.orEmpty() })
    }
}

/** 一張牌的簡寫。 */
private fun tileNotation(tile: Tile?): String = when (tile) {
    null -> "?"
    is Tile.Numeric -> "${tile.value}" + when (tile.suit) {
        Tile.Suit.Character -> "m"
        Tile.Suit.Dot -> "p"
        Tile.Suit.Bamboo -> "s"
    }
    Tile.Honor.East -> "1z"
    Tile.Honor.South -> "2z"
    Tile.Honor.West -> "3z"
    Tile.Honor.North -> "4z"
    Tile.Honor.White -> "5z"
    Tile.Honor.Green -> "6z"
    Tile.Honor.Red -> "7z"
    is Tile.Extension -> "${tile.typeId.namespace}:${tile.typeId.path}"
}

/** 帶有固定前綴的單行壓力測試回覆。 */
internal fun stressTestMessage(key: String, color: Formatting): MutableText = prefixedConfigMessage(stressText(key), color)

/** 桌數安排的顯示文字。 */
private fun modeText(mode: StressTestMode): Text = when (mode) {
    is StressTestMode.Fixed -> Text.translatableWithFallback(StressDebugKeys.MODE_FIXED, "Fixed %s tables", mode.tables)
    is StressTestMode.Ramp -> stressText(StressDebugKeys.MODE_RAMP)
}

/** 開始持續卡頓時的桌數；欄位名稱帶有卡頓的毫秒門檻。 */
private fun stutterEntry(report: StressTestReport): MutableText = Text.literal("\n  • ")
    .formatted(Formatting.GRAY)
    .append(Text.translatableWithFallback(StressDebugKeys.STUTTER, "Tables when stutter began (ticks over %s ms)", formatMillis(report.stutterLimitMillis)).formatted(Formatting.GRAY))
    .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
    .append(
        report.stutterTables?.let { Text.literal(it.toString()).formatted(Formatting.GOLD) }
            ?: stressText(StressDebugKeys.STUTTER_NONE).formatted(Formatting.GREEN),
    )

/** 暖機狀態的顯示文字。 */
private fun warmupText(report: StressTestReport): Text = if (report.warmupRemainingTicks > 0) {
    Text.translatableWithFallback(StressDebugKeys.WARMUP_RUNNING, "In progress, %s s left", (report.warmupRemainingTicks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND)
} else {
    Text.translatableWithFallback(StressDebugKeys.WARMUP_DONE, "Done, the first %s s are excluded", STRESS_WARMUP_TICKS / TICKS_PER_SECOND)
}

/** 推進節奏的翻譯鍵。 */
private fun paceKey(pace: StressTestPace): String = when (pace) {
    StressTestPace.REALTIME -> StressDebugKeys.PACE_REALTIME
    StressTestPace.FAST -> StressDebugKeys.PACE_FAST
}

/** 歷史背景工作環節的翻譯鍵。 */
private fun writerStageKey(stage: HistoryWriterStage): String = StressDebugKeys.WRITER_STAGE_PREFIX + stage.name.lowercase(Locale.ROOT)

/** 單一欄位：灰色欄位名稱加上指定顏色的值。 */
private fun entry(key: String, value: Text, valueColor: Formatting): MutableText = Text.literal("\n  • ")
    .formatted(Formatting.GRAY)
    .append(stressText(key).formatted(Formatting.GRAY))
    .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
    .append(value.copy().formatted(valueColor))

/** 縮排在上一個欄位下的子欄位。 */
private fun subEntry(name: MutableText, value: String): MutableText = Text.literal("\n      ◦ ")
    .formatted(Formatting.DARK_GRAY)
    .append(name.formatted(Formatting.GRAY))
    .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
    .append(Text.literal(value).formatted(Formatting.AQUA))

/** 以 [key] 建立帶英文備援的本地化文字。 */
private fun stressText(key: String): MutableText = Text.translatableWithFallback(key, STRESS_FALLBACKS[key] ?: key)

/** 以平均／第 95 百分位／最大值格式化毫秒數。 */
private fun formatTriple(average: Double, p95: Double, max: Double): String = "${formatMillis(average)} / ${formatMillis(p95)} / ${formatMillis(max)} ms"

/** 以平均／最大值格式化毫秒數。 */
private fun formatAverageMax(summary: TimingSummary): String = "${formatMillis(summary.averageMillis)} / ${formatMillis(summary.maxMillis)} ms"

/** 各門檻的超時 tick 比例，例如「1.5% / 0.2% / 0.0%」。 */
private fun formatSlowTicks(report: StressTestReport): String = report.slowTicks.values.joinToString(" / ") { count ->
    val ratio = if (report.measuredTicks == 0L) 0.0 else count * PERCENT / report.measuredTicks
    "${formatMillis(ratio)}%"
}

/** 歷史事件的產生與處理數量及每秒速率。 */
private fun formatEvents(report: StressTestReport): String = "${report.eventsProduced} / ${report.eventsWritten} " +
    "(${formatMillis(perSecond(report.eventsProduced, report.measuredSeconds))} / ${formatMillis(perSecond(report.eventsWritten, report.measuredSeconds))} /s)"

/** 每秒速率；沒有經過時間時為 0。 */
private fun perSecond(count: Long, seconds: Double): Double = if (seconds <= 0.0) 0.0 else count / seconds

/** 以一位小數格式化。 */
private fun formatMillis(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

/** 把 tick 數換算成秒數。 */
private fun formatSeconds(ticks: Long): String = "${ticks / TICKS_PER_SECOND} s"

/** 一秒的伺服器 tick 數。 */
private const val TICKS_PER_SECOND = 20

/** 比例換算百分比。 */
private const val PERCENT = 100.0

/** 語言檔沒有對應文字時使用的英文。 */
private val STRESS_FALLBACKS: Map<String, String> = mapOf(
    StressDebugKeys.STARTED to "Stress test started",
    StressDebugKeys.INTEGRATED_SERVER_HINT to "This is a single-player integrated server; the client shares its resources, so treat the numbers as rough. Use a dedicated server to measure capacity.",
    StressDebugKeys.BUSY to "A stress test is already running",
    StressDebugKeys.INVALID_SCENARIO to "Invalid stress test scenario",
    StressDebugKeys.INVALID_PACE to "Invalid stress test pace; use realtime or fast",
    StressDebugKeys.INVALID_HISTORY_MODE to "Invalid stress test history mode; use write, encode or off",
    StressDebugKeys.STORAGE_UNAVAILABLE to "The stress test database could not be opened",
    StressDebugKeys.NOT_RUNNING to "There is no running stress test",
    StressDebugKeys.NO_REPORT to "There is no stress test report yet",
    StressDebugKeys.CLEARED to "The stress test database was deleted",
    StressDebugKeys.NOTHING_TO_CLEAR to "There is no stress test database to delete",
    StressDebugKeys.CLEAR_WHILE_RUNNING to "Stop the stress test before deleting its database",
    StressDebugKeys.TITLE_RUNNING to "Stress test running",
    StressDebugKeys.TITLE_STOPPED to "Stress test report",
    StressDebugKeys.SCENARIO to "Scenario",
    StressDebugKeys.MODE to "Tables",
    StressDebugKeys.MODE_RAMP to "Ramp",
    StressDebugKeys.PACE to "Pace",
    StressDebugKeys.PACE_REALTIME to "Real time",
    StressDebugKeys.PACE_FAST to "Fast",
    StressDebugKeys.HISTORY_MODE to "History",
    StressDebugKeys.HISTORY_MODE_PREFIX + "write" to "Record and write to the database",
    StressDebugKeys.HISTORY_MODE_PREFIX + "encode" to "Record and encode only",
    StressDebugKeys.HISTORY_MODE_PREFIX + "off" to "Not recorded",
    StressDebugKeys.TABLES to "Running tables",
    StressDebugKeys.COMPLETED to "Completed matches",
    StressDebugKeys.FAILED to "Stalled matches",
    StressDebugKeys.ELAPSED to "Elapsed",
    StressDebugKeys.WARMUP to "Warm-up",
    StressDebugKeys.TICK to "Tick time (avg / p95 / max)",
    StressDebugKeys.SLOW_TICKS to "Ticks over 50 / 100 / 250 ms",
    StressDebugKeys.STEP to "Step time (avg / p95 / max)",
    StressDebugKeys.STEP_BREAKDOWN to "Step breakdown (avg / max)",
    StressDebugKeys.STEP_STAGE_PREFIX + "ai_decision" to "AI decision",
    StressDebugKeys.STEP_STAGE_PREFIX + "rules_and_state" to "Rules and state",
    StressDebugKeys.STEP_STAGE_PREFIX + "snapshot_sync" to "Snapshot sync",
    StressDebugKeys.STEP_STAGE_PREFIX + "history_recording" to "History recording",
    StressDebugKeys.PEAK_PER_TICK to "Most in one tick (steps / history events)",
    StressDebugKeys.EVENTS to "History events produced / processed (per second)",
    StressDebugKeys.WRITER_BREAKDOWN to "History background work (avg / max × count)",
    StressDebugKeys.WRITER_STAGE_PREFIX + "disk_usage" to "Disk usage check",
    StressDebugKeys.WRITER_STAGE_PREFIX + "decision_sync" to "Match status sync",
    StressDebugKeys.WRITER_STAGE_PREFIX + "tombstones" to "Deleted match check",
    StressDebugKeys.WRITER_STAGE_PREFIX + "encode" to "Encoding",
    StressDebugKeys.WRITER_STAGE_PREFIX + "batch_write" to "Batch write",
    StressDebugKeys.WRITER_STAGE_PREFIX + "archive" to "Replay archiving",
    StressDebugKeys.WRITER_STAGE_PREFIX + "maintenance" to "Periodic maintenance",
    StressDebugKeys.WRITER_STAGE_PREFIX + "storage_stats" to "Storage statistics",
    StressDebugKeys.PENDING to "History backlog (now / peak / capacity)",
    StressDebugKeys.LOST to "Matches with lost history",
    StressDebugKeys.WRITER to "History background work",
    StressDebugKeys.WRITER_OK to "OK",
    StressDebugKeys.WRITER_FAILED to "Failed",
    StressDebugKeys.MEMORY to "Memory used",
    StressDebugKeys.GC to "Garbage collection (count / time)",
    StressDebugKeys.TIME_SERIES to "Per-second data file",
    StressDebugKeys.STOP_REASON to "Stop reason",
    StressDebugKeys.SUSTAINED to "Most tables sustained",
    StressDebugKeys.STUTTER_NONE to "Not yet",
    StressDebugKeys.REASON_PREFIX + "requested" to "Stopped by request",
    StressDebugKeys.REASON_PREFIX + "falling_behind" to "The server fell behind 20 ticks per second",
    StressDebugKeys.REASON_PREFIX + "history_backlog" to "History backlog reached the limit",
    StressDebugKeys.REASON_PREFIX + "history_lost" to "History was lost",
    StressDebugKeys.REASON_PREFIX + "history_writer_failed" to "History background work failed",
    StressDebugKeys.REASON_PREFIX + "server_stopping" to "Server stopping",
    StressDebugKeys.REASON_PREFIX + "run_failed" to "The stress test failed unexpectedly",
)
