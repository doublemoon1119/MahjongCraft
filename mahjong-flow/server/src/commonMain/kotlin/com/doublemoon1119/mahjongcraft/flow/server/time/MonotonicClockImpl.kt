package com.doublemoon1119.mahjongcraft.flow.server.time

import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import kotlin.time.TimeSource

/**
 * 以 Kotlin 單調時間來源提供目前 runtime session 的經過毫秒數——真實系統時間，不會因為遊戲暫停而
 * 停止前進，因此**不透過 Koin 注入**（沒有 `@Single`）：正式環境一定要用平台層知道「遊戲是否正在
 * 運行」的實作（例如 Fabric 的 tick 計數時鐘），理由見 [MonotonicClock] KDoc。這裡只給整合測試在
 * 不需要模擬暫停情境時，直接建構使用一個真的會前進的時鐘。
 */
class MonotonicClockImpl : MonotonicClock {
    /** 建立此時間來源時的單調時間標記。 */
    private val origin = TimeSource.Monotonic.markNow()

    /** 取得自 [origin] 起算的經過毫秒數。 */
    override fun nowMillis(): Long = origin.elapsedNow().inWholeMilliseconds
}
