package com.doublemoon1119.mahjongcraft.platform.fabric.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/** 驗證每位玩家的要求頻率限制：連續上限、隨時間恢復、丟棄紀錄節流與離線清除。 */
class PlayerRequestRateLimiterTest {
    /** 測試用的時間，單位為奈秒。 */
    private var now = 0L

    private val limiter = PlayerRequestRateLimiter(
        limit = C2SRateLimit.PerPlayer(burst = 2, refillInterval = 100.milliseconds),
        nanoTime = { now },
    )
    private val player = Uuid.random()
    private val otherPlayer = Uuid.random()

    /** 超過連續上限的要求被丟棄；其他玩家的名額不受影響。 */
    @Test
    fun `requests beyond the burst are dropped per player`() {
        assertEquals(PlayerRequestRateLimiter.Result.Accepted, limiter.tryAcquire(player))
        assertEquals(PlayerRequestRateLimiter.Result.Accepted, limiter.tryAcquire(player))
        assertEquals(PlayerRequestRateLimiter.Result.Dropped(shouldLog = true), limiter.tryAcquire(player))
        assertEquals(PlayerRequestRateLimiter.Result.Accepted, limiter.tryAcquire(otherPlayer))
    }

    /** 每經過一個恢復間隔恢復一個名額，且不超過連續上限。 */
    @Test
    fun `quota recovers over time up to the burst`() {
        repeat(2) { limiter.tryAcquire(player) }

        now += 100.milliseconds.inWholeNanoseconds
        assertEquals(PlayerRequestRateLimiter.Result.Accepted, limiter.tryAcquire(player))
        assertEquals(PlayerRequestRateLimiter.Result.Dropped(shouldLog = true), limiter.tryAcquire(player))

        now += 1_000.milliseconds.inWholeNanoseconds
        repeat(2) { assertEquals(PlayerRequestRateLimiter.Result.Accepted, limiter.tryAcquire(player)) }
        assertEquals(PlayerRequestRateLimiter.Result.Dropped(shouldLog = true), limiter.tryAcquire(player))
    }

    /** 持續被丟棄的要求每個恢復間隔最多回報一次需要記錄。 */
    @Test
    fun `dropped requests are reported at most once per refill interval`() {
        repeat(2) { limiter.tryAcquire(player) }

        assertEquals(PlayerRequestRateLimiter.Result.Dropped(shouldLog = true), limiter.tryAcquire(player))
        assertEquals(PlayerRequestRateLimiter.Result.Dropped(shouldLog = false), limiter.tryAcquire(player))
        now += 50.milliseconds.inWholeNanoseconds
        assertEquals(PlayerRequestRateLimiter.Result.Dropped(shouldLog = false), limiter.tryAcquire(player))
        now += 99.milliseconds.inWholeNanoseconds
        assertEquals(PlayerRequestRateLimiter.Result.Accepted, limiter.tryAcquire(player))
        assertEquals(PlayerRequestRateLimiter.Result.Dropped(shouldLog = true), limiter.tryAcquire(player))
    }

    /** 清除後玩家重新取得完整名額。 */
    @Test
    fun `forgetting a player restores the full burst`() {
        repeat(2) { limiter.tryAcquire(player) }

        limiter.forget(player)

        repeat(2) { assertEquals(PlayerRequestRateLimiter.Result.Accepted, limiter.tryAcquire(player)) }
    }

    /** 識別字串不可空白且有長度上限。 */
    @Test
    fun `client request ids must be non blank and bounded`() {
        assertEquals(true, ClientRequestIds.isValid("x".repeat(ClientRequestIds.MAX_LENGTH)))
        assertEquals(false, ClientRequestIds.isValid("x".repeat(ClientRequestIds.MAX_LENGTH + 1)))
        assertEquals(false, ClientRequestIds.isValid(" "))
    }
}
