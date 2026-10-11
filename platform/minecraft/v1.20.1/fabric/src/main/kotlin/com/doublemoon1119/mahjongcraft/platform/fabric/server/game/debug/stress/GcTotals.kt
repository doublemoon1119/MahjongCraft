package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import java.lang.management.ManagementFactory

/**
 * JVM 記憶體回收的累計次數與耗時。
 *
 * @property count 回收次數。
 * @property millis JVM 回報的累計回收耗時毫秒數；以 G1 等分代回收器而言即為暫停時間。
 */
data class GcTotals(
    val count: Long,
    val millis: Long,
) {
    /** 與較早讀數 [other] 的差額。 */
    operator fun minus(other: GcTotals): GcTotals = GcTotals(count - other.count, millis - other.millis)
    companion object {
        /** 讀取目前所有回收器的累計值；回收器不提供數值時視為 0。 */
        fun read(): GcTotals = ManagementFactory.getGarbageCollectorMXBeans().fold(GcTotals(0, 0)) { totals, bean ->
            GcTotals(totals.count + bean.collectionCount.coerceAtLeast(0), totals.millis + bean.collectionTime.coerceAtLeast(0))
        }
    }
}
