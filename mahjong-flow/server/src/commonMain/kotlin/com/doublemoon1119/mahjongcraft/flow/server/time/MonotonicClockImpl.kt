package com.doublemoon1119.mahjongcraft.flow.server.time

import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import kotlin.time.TimeSource

/**
 * 以 Kotlin 單調時間來源計算自實例建立起經過的毫秒數。
 *
 * 此時間來源不會隨對局暫停而停止，也未登記為注入元件。適合不需模擬暫停的整合測試；
 * 需要暫停時間語意的執行環境應提供符合 [MonotonicClock] 契約的其他實作。
 */
class MonotonicClockImpl : MonotonicClock {
    /** 建立此時間來源時的單調時間標記。 */
    private val origin = TimeSource.Monotonic.markNow()

    /** 取得自 [origin] 起算的經過毫秒數。 */
    override fun nowMillis(): Long = origin.elapsedNow().inWholeMilliseconds
}
