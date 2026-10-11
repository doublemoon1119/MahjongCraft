package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

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
