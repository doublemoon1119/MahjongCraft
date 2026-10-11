package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryWriterObserver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryWriterStage
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
