package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryWriterStage

/**
 * 壓力測試一次執行的結果與目前數據。
 *
 * 除了場數與即時狀態，所有統計都只涵蓋暖機結束之後（見 [StressRunOptions.warmupSeconds]）。
 *
 * @property scenarioId 對局情境識別碼。
 * @property mode 桌數安排。
 * @property pace 每桌推進節奏。
 * @property historyMode 歷史處理方式。
 * @property running 是否仍在執行。
 * @property tables 目前同時進行的桌數。
 * @property completedMatches 開始以來已打完的場數。
 * @property failedMatches 開始以來卡住而中止的場數。
 * @property elapsedTicks 開始以來已經過的伺服器 tick 數。
 * @property warmupTicks 暖機的 tick 數；0 表示不暖機。
 * @property warmupRemainingTicks 暖機剩餘的 tick 數；暖機已結束時為 0。
 * @property measuredSeconds 暖機結束後經過的實際秒數。
 * @property tickAverageMillis 最近一段時間 tick 本身處理時間的平均毫秒數；只供診斷，不含 tick 之間執行的工作。
 * @property tickP95Millis 最近一段時間 tick 本身處理時間的第 95 百分位毫秒數。
 * @property tickMaxMillis 最近一段時間 tick 本身處理時間的最大毫秒數。
 * @property tickIntervalAverageMillis 最近一段時間實際 tick 間隔（相鄰兩個 tick 開始的時間差）的平均毫秒數。
 * @property tickIntervalP95Millis 最近一段時間實際 tick 間隔的第 95 百分位毫秒數。
 * @property tickIntervalMaxMillis 最近一段時間實際 tick 間隔的最大毫秒數。
 * @property lagMillis 目前相對每 tick 50 ms 節奏的落後毫秒數；見 [StressSafetyValve]。
 * @property advanceMillisPerSecond 暖機結束後每秒 MahjongCraft 推進（各桌推進協程在主執行緒上實際執行的片段）的平均毫秒數。
 * @property stepAverageMillis 最近一段時間單桌單步 MahjongCraft 推進耗時的平均毫秒數：這一步在主執行緒上實際執行的片段加總，
 *   不含等待 AI 結果的時間。
 *
 * @property stepP95Millis 最近一段時間單桌單步 MahjongCraft 推進耗時的第 95 百分位毫秒數。
 * @property stepMaxMillis 最近一段時間單桌單步 MahjongCraft 推進耗時的最大毫秒數。
 * @property stepStages 單步各環節的耗時；見 [StressStepStage]。
 * @property historyRecording 每 tick 歷史記錄的耗時。
 * @property aiDecisions 有結果的 AI 決策數。
 * @property aiTimeouts 等待超過上限而使用固定命令的 AI 決策數。
 * @property aiPreviousStillRunning 同一局先前逾時的策略呼叫尚未結束，直接使用固定命令的 AI 決策數。
 * @property aiStrategyFailures 策略丟出一般例外而使用固定命令的 AI 決策數。
 * @property aiLatencyAverageMillis 暖機結束後 AI 決策延遲（開始決策到得到結果，含排隊）的平均毫秒數。
 * @property aiLatencyP95Millis 暖機結束後 AI 決策延遲的第 95 百分位毫秒數。
 * @property aiLatencyP99Millis 暖機結束後 AI 決策延遲的第 99 百分位毫秒數。
 * @property aiLatencyMaxMillis 暖機結束後 AI 決策延遲的最大毫秒數。
 * @property staleDecisions 權威遊戲已改變而沒有套用的 AI 決策數。
 * @property unfinishedStrategyCalls 目前尚未真正結束的策略工作數。
 * @property unfinishedStrategyPeak 暖機結束後同時存在、尚未真正結束的策略工作數的最大值，包含 tick 之間短暫出現的工作。
 * @property strategyCapacity 同時存在、尚未結束的策略工作上限。
 * @property writerStages 歷史背景工作各環節的耗時；只列出有發生的環節。
 * @property eventsProduced 新加入待寫佇列的歷史事件數。
 * @property eventsWritten 背景工作處理完（寫入或編碼後確認移除）的歷史事件數。
 * @property maxStepsInTick 單一 tick 內完成的最多步數。
 * @property maxEventsInTick 單一 tick 內產生的最多歷史事件數。
 * @property measuredTicks 列入統計的 tick 數。
 * @property slowTicks 以 [STRESS_SLOW_TICK_THRESHOLDS_MILLIS] 的每個門檻毫秒數為鍵，耗時超過門檻的 tick 數。
 * @property gc 記憶體回收次數與耗時。
 * @property stutterLimitMillis 單一 tick 間隔超過這個毫秒數就算一次卡頓。
 * @property stutterTables 第一次判定持續卡頓時的桌數；尚未發生時為 null。
 * @property pendingEvents 目前歷史待寫佇列中的事件數。
 * @property pendingPeak 歷史待寫佇列的最大事件數。
 * @property pendingCapacity 歷史待寫佇列的容量。
 * @property lostSegments 出現序號缺口（遺失歷史）的場次數。
 * @property writerFailed 歷史背景工作是否發生錯誤或暫停。
 * @property usedMemoryMiB 目前 JVM 已使用的記憶體 MiB。
 * @property timeSeriesFile 每秒時間序列 CSV 的檔名；無法建立時為 null。
 * @property stopReason 停止原因；仍在執行時為 null。
 * @property sustainedTables 爬坡模式因安全閥停止時，已完整撐過一個階段的最大桌數；其他情況為 null。
 */
data class StressTestReport(
    val scenarioId: String,
    val mode: StressTestMode,
    val pace: StressTestPace,
    val historyMode: StressHistoryMode,
    val running: Boolean,
    val tables: Int,
    val completedMatches: Int,
    val failedMatches: Int,
    val elapsedTicks: Long,
    val warmupTicks: Long,
    val warmupRemainingTicks: Long,
    val measuredSeconds: Double,
    val tickAverageMillis: Double,
    val tickP95Millis: Double,
    val tickMaxMillis: Double,
    val tickIntervalAverageMillis: Double,
    val tickIntervalP95Millis: Double,
    val tickIntervalMaxMillis: Double,
    val lagMillis: Double,
    val advanceMillisPerSecond: Double,
    val stepAverageMillis: Double,
    val stepP95Millis: Double,
    val stepMaxMillis: Double,
    val stepStages: Map<StressStepStage, TimingSummary>,
    val historyRecording: TimingSummary,
    val aiDecisions: Long,
    val aiTimeouts: Long,
    val aiPreviousStillRunning: Long,
    val aiLatencyAverageMillis: Double,
    val aiLatencyP95Millis: Double,
    val aiLatencyP99Millis: Double,
    val aiLatencyMaxMillis: Double,
    val staleDecisions: Long,
    val unfinishedStrategyCalls: Int,
    val unfinishedStrategyPeak: Int,
    val strategyCapacity: Int,
    val writerStages: Map<HistoryWriterStage, TimingSummary>,
    val eventsProduced: Long,
    val eventsWritten: Long,
    val maxStepsInTick: Int,
    val maxEventsInTick: Int,
    val measuredTicks: Long,
    val slowTicks: Map<Int, Long>,
    val gc: GcTotals,
    val stutterLimitMillis: Double,
    val stutterTables: Int?,
    val pendingEvents: Int,
    val pendingPeak: Int,
    val pendingCapacity: Int,
    val lostSegments: Int,
    val writerFailed: Boolean,
    val usedMemoryMiB: Long,
    val timeSeriesFile: String?,
    val stopReason: StressStopReason?,
    val sustainedTables: Int?,
    val aiStrategyFailures: Long = 0,
)
