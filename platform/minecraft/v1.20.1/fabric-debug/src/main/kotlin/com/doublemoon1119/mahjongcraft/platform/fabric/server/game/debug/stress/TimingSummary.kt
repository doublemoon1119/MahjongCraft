package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

/**
 * 一組耗時的統計結果。
 *
 * @property count 筆數。
 * @property averageMillis 平均毫秒數；沒有資料時為 0。
 * @property maxMillis 最大毫秒數；沒有資料時為 0。
 * @property totalMillis 總毫秒數。
 */
data class TimingSummary(
    val count: Long,
    val averageMillis: Double,
    val maxMillis: Double,
    val totalMillis: Double,
) {
    companion object {
        /** 沒有資料的統計。 */
        val EMPTY: TimingSummary = TimingSummary(count = 0, averageMillis = 0.0, maxMillis = 0.0, totalMillis = 0.0)
    }
}
