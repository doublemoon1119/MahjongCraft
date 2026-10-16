package com.doublemoon1119.mahjongcraft.platform.fabric.network

import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * 依 [C2SRateLimit.PerPlayer] 決定每位玩家的要求是否接受；可由網路執行緒同時呼叫。
 *
 * 被丟棄的要求每位玩家每個恢復間隔最多回報一次需要記錄，避免被丟棄的要求灌爆 log。
 *
 * @property limit 頻率限制。
 * @property nanoTime 單調遞增的時間來源，單位為奈秒。
 */
internal class PlayerRequestRateLimiter(
    private val limit: C2SRateLimit.PerPlayer,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    /**
     * 一位玩家目前的名額。
     *
     * @property tokens 目前可用的名額。
     * @property refilledAt 上次計算恢復名額的時間。
     * @property dropReportedAt 上次回報丟棄需要記錄的時間；尚未回報時為 null。
     */
    private class Bucket(var tokens: Int, var refilledAt: Long, var dropReportedAt: Long?)

    /** 每位玩家的名額。 */
    private val buckets = ConcurrentHashMap<Uuid, Bucket>()

    /** 恢復一個名額所需的奈秒數。 */
    private val refillNanos: Long = limit.refillInterval.inWholeNanoseconds.coerceAtLeast(1)

    /**
     * 嘗試接受 [playerId] 的一個要求。
     *
     * @param playerId 送出要求的玩家。
     * @return 接受或丟棄的結果。
     */
    fun tryAcquire(playerId: Uuid): Result {
        val now = nanoTime()
        val bucket = buckets.computeIfAbsent(playerId) { Bucket(limit.burst, now, null) }
        return synchronized(bucket) {
            val refilled = (now - bucket.refilledAt) / refillNanos
            if (refilled > 0) {
                bucket.tokens = (bucket.tokens + refilled).coerceAtMost(limit.burst.toLong()).toInt()
                bucket.refilledAt += refilled * refillNanos
            }
            if (bucket.tokens > 0) {
                bucket.tokens--
                Result.Accepted
            } else {
                val shouldLog = bucket.dropReportedAt?.let { now - it >= refillNanos } ?: true
                if (shouldLog) bucket.dropReportedAt = now
                Result.Dropped(shouldLog)
            }
        }
    }

    /**
     * 移除玩家的名額紀錄，例如玩家離線時。
     *
     * @param playerId 要移除的玩家。
     */
    fun forget(playerId: Uuid) {
        buckets.remove(playerId)
    }

    /** 一次要求的處理結果。 */
    sealed interface Result {
        /** 要求被接受。 */
        data object Accepted : Result

        /**
         * 要求超過頻率限制而被丟棄。
         *
         * @property shouldLog 這次丟棄是否需要記錄；同一位玩家每個恢復間隔最多一次。
         */
        data class Dropped(val shouldLog: Boolean) : Result
    }
}
