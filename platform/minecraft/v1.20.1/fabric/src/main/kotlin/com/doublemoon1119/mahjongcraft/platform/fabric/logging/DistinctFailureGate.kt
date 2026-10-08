package com.doublemoon1119.mahjongcraft.platform.fabric.logging

/**
 * 決定一個反覆讀取的值失敗時是否要記錄 log：同一個失敗值只記錄一次，值改變或中間成功過才再記錄。
 *
 * 用在每幀或每 tick 都會讀取的同步資料，避免同一份壞資料洗版。
 *
 * @param T 失敗值的型別。
 */
internal class DistinctFailureGate<T> {
    /** 最近一次已記錄的失敗值；沒有時為 null。 */
    private var reported: T? = null

    /** 是否已有記錄過的失敗值。 */
    private var hasReported = false

    /**
     * 讀取 [value] 失敗；回傳這次是否要記錄。
     *
     * @param value 讀取失敗的值。
     * @return 與上次記錄的失敗值不同，或上次之後成功讀取過時為 true。
     */
    @Synchronized
    fun shouldReport(value: T): Boolean {
        if (hasReported && reported == value) return false
        reported = value
        hasReported = true
        return true
    }

    /** 讀取成功；之後的失敗會再次記錄。 */
    @Synchronized
    fun clear() {
        reported = null
        hasReported = false
    }
}
