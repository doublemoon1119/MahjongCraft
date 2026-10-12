package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import java.lang.management.ManagementFactory

/**
 * JVM 記憶體回收的累計次數與耗時。
 *
 * @property count 回收次數。
 * @property millis 各 JVM 回收器回報的累計回收耗時毫秒數總和；不等同應用程式停止執行的精確時間。
 */
data class GcTotals(
    val count: Long,
    val millis: Long,
) {
    /**
     * 計算與另一筆讀數的差額，不額外限制結果為非負值。
     *
     * @param other 通常為較早取得的累計讀數。
     * @return 次數與耗時分別相減的結果。
     */
    operator fun minus(other: GcTotals): GcTotals = GcTotals(count - other.count, millis - other.millis)
    companion object {
        /** 讀取目前所有回收器的累計值；回收器不提供數值時視為 0。 */
        fun read(): GcTotals = ManagementFactory.getGarbageCollectorMXBeans().fold(GcTotals(0, 0)) { totals, bean ->
            GcTotals(totals.count + bean.collectionCount.coerceAtLeast(0), totals.millis + bean.collectionTime.coerceAtLeast(0))
        }
    }
}
