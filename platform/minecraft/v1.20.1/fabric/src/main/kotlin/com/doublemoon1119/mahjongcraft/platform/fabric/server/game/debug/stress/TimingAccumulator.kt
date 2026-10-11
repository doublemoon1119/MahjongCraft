package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.stress

import kotlin.time.Duration
import kotlin.time.DurationUnit

/** 累計筆數、總耗時與最大耗時；不保留個別數值。 */
class TimingAccumulator {
    /** 筆數。 */
    private var count = 0L

    /** 總耗時。 */
    private var total = Duration.ZERO

    /** 最大耗時。 */
    private var max = Duration.ZERO

    /** 加入一筆耗時。 */
    fun add(duration: Duration) {
        count++
        total += duration
        if (duration > max) max = duration
    }

    /** 目前的統計結果。 */
    fun summary(): TimingSummary = if (count == 0L) {
        TimingSummary.EMPTY
    } else {
        TimingSummary(
            count = count,
            averageMillis = total.toDouble(DurationUnit.MILLISECONDS) / count,
            maxMillis = max.toDouble(DurationUnit.MILLISECONDS),
            totalMillis = total.toDouble(DurationUnit.MILLISECONDS),
        )
    }
}
