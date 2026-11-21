package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import kotlin.math.ceil

/**
 * 判斷壓力測試是否該停止，並偵測持續卡頓。
 *
 * 以實際 tick 間隔判斷伺服器是否跟不上：連續 [StressSafetyThresholds.sustainedWindows] 個不重疊視窗的平均間隔都超過門檻就停止；
 * 另外追蹤相對目標節奏的落後量（每個 tick 加上間隔與目標的差，最低為 0），伺服器追上時會下降，超過
 * [StressSafetyThresholds.maxLagMillis] 就緊急停止。爬坡進入新的階段時以 [resetLag] 重設落後量。持續卡頓（間隔尖峰）只回報
 * 狀態、不停止，讓同一次測試同時量到「開始卡頓」與「撐不住」兩個桌數。歷史遺失、寫入失敗與積壓達到比例門檻則立即停止，讓測試
 * 在真的遺失歷史之前停下。
 *
 * @property thresholds 停止門檻。
 */
class StressSafetyValve(private val thresholds: StressSafetyThresholds) {
    /** 最近 [StressSafetyThresholds.windowTicks] 個 tick 是否算作卡頓。 */
    private val stutterFlags = ArrayDeque<Boolean>(thresholds.windowTicks)

    /** [stutterFlags] 中的卡頓次數。 */
    private var stutters = 0

    /** 目前視窗已記錄的 tick 數。 */
    private var windowCount = 0

    /** 目前視窗的間隔總和。 */
    private var windowTotalMillis = 0.0

    /** 連續跟不上的視窗數。 */
    private var slowWindows = 0

    /** 相對目標節奏的落後毫秒數。 */
    var lagMillis: Double = 0.0
        private set

    /** 最近一段時間是否持續卡頓。 */
    val stuttering: Boolean get() = stutters >= thresholds.stutterTicks

    /**
     * 記錄一個 tick 的實際間隔；伺服器持續跟不上或落後太多時回傳停止原因。
     *
     * @param intervalMillis 這個 tick 與上一個 tick 開始的時間差毫秒數。
     * @param countStutter 這個 tick 是否列入卡頓判斷；暖機期間不列入，但仍會判斷是否落後。
     * @return 持續落後或累計落後超過上限時的停止原因；否則為 null。
     */
    fun recordTick(intervalMillis: Double, countStutter: Boolean): StressStopReason? {
        val stutter = countStutter && intervalMillis > thresholds.stutterLimitMillis
        stutterFlags.addLast(stutter)
        if (stutter) stutters++
        if (stutterFlags.size > thresholds.windowTicks && stutterFlags.removeFirst()) stutters--
        lagMillis = (lagMillis + intervalMillis - thresholds.targetIntervalMillis).coerceAtLeast(0.0)
        windowCount++
        windowTotalMillis += intervalMillis
        if (windowCount == thresholds.windowTicks) {
            slowWindows = if (windowTotalMillis / windowCount > thresholds.fallingBehindAverageMillis) slowWindows + 1 else 0
            windowCount = 0
            windowTotalMillis = 0.0
        }
        return if (slowWindows >= thresholds.sustainedWindows || lagMillis > thresholds.maxLagMillis) StressStopReason.FALLING_BEHIND else null
    }

    /** 重設相對目標節奏的落後量，例如爬坡進入新的階段時。 */
    fun resetLag() {
        lagMillis = 0.0
    }

    /**
     * 檢查歷史寫入狀態。
     *
     * @param pendingEvents 待寫佇列中的事件數。
     * @param capacity 待寫佇列容量。
     * @param lostSegments 出現序號缺口的場次數。
     * @param writerFailed 寫入元件是否發生錯誤或暫停寫入。
     * @return 優先回報歷史遺失，其次寫入失敗與積壓；未達門檻時為 null。
     */
    fun checkHistory(pendingEvents: Int, capacity: Int, lostSegments: Int, writerFailed: Boolean): StressStopReason? = when {
        lostSegments > 0 -> StressStopReason.HISTORY_LOST
        writerFailed -> StressStopReason.HISTORY_WRITER_FAILED
        capacity > 0 && pendingEvents >= ceil(capacity * thresholds.backlogRatio) -> StressStopReason.HISTORY_BACKLOG
        else -> null
    }
}
