package com.doublemoon1119.mahjongcraft.flow.server.time

import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import org.koin.core.annotation.Single
import kotlin.time.TimeSource

/** 以 Kotlin 單調時間來源提供目前 runtime session 的經過毫秒數。 */
@Single
class MonotonicClockImpl : MonotonicClock {
    /** 建立此時間來源時的單調時間標記。 */
    private val origin = TimeSource.Monotonic.markNow()

    /** 取得自 [origin] 起算的經過毫秒數。 */
    override fun nowMillis(): Long = origin.elapsedNow().inWholeMilliseconds
}
