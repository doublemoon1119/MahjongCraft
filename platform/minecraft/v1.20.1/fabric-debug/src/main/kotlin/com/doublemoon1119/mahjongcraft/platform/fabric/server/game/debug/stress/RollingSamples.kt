package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import kotlin.math.ceil

/**
 * 保留最近 [capacity] 筆數值的滾動統計。
 *
 * @property capacity 保留的筆數上限。
 */
class RollingSamples(private val capacity: Int) {
    init {
        require(capacity > 0) { "Sample capacity must be positive" }
    }

    /** 依加入順序保留的數值。 */
    private val values = ArrayDeque<Double>(capacity)

    /** 目前保留的筆數。 */
    val size: Int get() = values.size

    /**
     * 加入一筆數值；超過容量時捨棄最舊的一筆。
     *
     * @param value 新樣本數值。
     */
    fun add(value: Double) {
        if (values.size == capacity) values.removeFirst()
        values.addLast(value)
    }

    /** 平均值；沒有數值時為 0。 */
    fun average(): Double = if (values.isEmpty()) 0.0 else values.average()

    /** 最大值；沒有數值時為 0。 */
    fun max(): Double = values.maxOrNull() ?: 0.0

    /**
     * 依向上取整的分位排名取得目前保留樣本的分位數。
     *
     * @param fraction 分位比例，須介於 0 與 1；例如 0.95 代表 P95。
     * @return 排序後對應排名的樣本；沒有樣本時為零。
     * @throws IllegalArgumentException 比例不在有效範圍時拋出。
     */
    fun percentile(fraction: Double): Double {
        require(fraction in 0.0..1.0) { "Percentile fraction must be between 0 and 1" }
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val rank = ceil(fraction * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }
}
