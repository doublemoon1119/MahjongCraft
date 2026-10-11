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

    /** 加入一筆數值；超過容量時捨棄最舊的一筆。 */
    fun add(value: Double) {
        if (values.size == capacity) values.removeFirst()
        values.addLast(value)
    }

    /** 平均值；沒有數值時為 0。 */
    fun average(): Double = if (values.isEmpty()) 0.0 else values.average()

    /** 最大值；沒有數值時為 0。 */
    fun max(): Double = values.maxOrNull() ?: 0.0

    /** 第 [fraction] 分位數（例如 0.95），以最接近的排名取值；沒有數值時為 0。 */
    fun percentile(fraction: Double): Double {
        require(fraction in 0.0..1.0) { "Percentile fraction must be between 0 and 1" }
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val rank = ceil(fraction * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }
}
