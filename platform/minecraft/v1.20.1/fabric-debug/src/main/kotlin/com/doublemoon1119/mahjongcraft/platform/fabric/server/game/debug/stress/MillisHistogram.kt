package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import kotlin.math.ceil

/**
 * 以固定刻度累計毫秒數的直方圖：記憶體用量固定，可統計長時間量測中全部數值的分位數。
 *
 * 分位數以落點那一格的上緣回報（不超過實際最大值），誤差最多 [resolutionMillis]；超過 [limitMillis] 的數值歸入最後一格，
 * 分位數落在最後一格時回報實際最大值。
 *
 * @property resolutionMillis 每格的寬度毫秒數。
 * @property limitMillis 有獨立刻度的上限毫秒數。
 */
class MillisHistogram(
    private val resolutionMillis: Double = DEFAULT_RESOLUTION_MILLIS,
    private val limitMillis: Double = DEFAULT_LIMIT_MILLIS,
) {
    init {
        require(resolutionMillis > 0.0) { "Histogram resolution must be positive" }
        require(limitMillis >= resolutionMillis) { "Histogram limit must be at least one resolution step" }
    }

    /** 每格的筆數；最後一格收容超過上限的數值。 */
    private val counts = LongArray(ceil(limitMillis / resolutionMillis).toInt() + 1)

    /** 累計的筆數。 */
    var count: Long = 0L
        private set

    /** 累計的總毫秒數。 */
    private var totalMillis = 0.0

    /** 累計的最大毫秒數。 */
    private var maxMillis = 0.0

    /**
     * 加入一筆毫秒數；負值視為零。
     *
     * @param millis 量得的有限毫秒數。
     */
    fun add(millis: Double) {
        val value = millis.coerceAtLeast(0.0)
        counts[(value / resolutionMillis).toInt().coerceAtMost(counts.lastIndex)]++
        count++
        totalMillis += value
        maxMillis = maxOf(maxMillis, value)
    }

    /** 平均值；沒有數值時為 0。 */
    fun average(): Double = if (count == 0L) 0.0 else totalMillis / count

    /** 最大值；沒有數值時為 0。 */
    fun max(): Double = maxMillis

    /**
     * 依向上取整的分位排名取得直方圖格子的上緣，最後一格改回報實際最大值。
     *
     * @param fraction 分位比例，須介於 0 與 1；例如 0.99 代表 P99。
     * @return 對應格子的近似毫秒數，不超過實際最大值；沒有樣本時為零。
     * @throws IllegalArgumentException 比例不在有效範圍時拋出。
     */
    fun percentile(fraction: Double): Double {
        require(fraction in 0.0..1.0) { "Percentile fraction must be between 0 and 1" }
        if (count == 0L) return 0.0
        val rank = ceil(fraction * count).toLong().coerceIn(1L, count)
        var seen = 0L
        for (index in counts.indices) {
            seen += counts[index]
            if (seen < rank) continue
            return if (index == counts.lastIndex) maxMillis else minOf((index + 1) * resolutionMillis, maxMillis)
        }
        return maxMillis
    }

    /** 預設刻度所在的伴生物件。 */
    private companion object {
        /** 預設每格寬度毫秒數。 */
        const val DEFAULT_RESOLUTION_MILLIS = 0.1

        /** 預設有獨立刻度的上限毫秒數。 */
        const val DEFAULT_LIMIT_MILLIS = 10_000.0
    }
}
