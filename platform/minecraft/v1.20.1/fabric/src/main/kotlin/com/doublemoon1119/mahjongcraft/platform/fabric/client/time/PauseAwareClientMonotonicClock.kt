package com.doublemoon1119.mahjongcraft.platform.fabric.client.time

import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/**
 * 跟著客戶端 tick 前進的 [MonotonicClock]：每個 tick 累加與上一個 tick 之間經過的時間，遊戲暫停時不累加。
 *
 * 單人遊戲暫停時內建伺服器也停止運作，這裡跟著停止，等待伺服器回覆的時間不會在暫停期間累積；連到專用伺服器時遊戲
 * 不會暫停，照常前進。伺服器邏輯使用的 [MonotonicClock] 跟著伺服器 tick 前進，連到專用伺服器時不會在客戶端前進，
 * 因此需要判斷客戶端等待多久的元件明確注入這個時鐘。
 */
class PauseAwareClientMonotonicClock : MonotonicClock {
    /** 未暫停期間累加的時間，單位為奈秒。 */
    private var elapsedNanos = 0L

    /** 上一個 tick 的系統單調時間；尚未經過任何 tick 時為 null。 */
    private var lastTickNanos: Long? = null

    /** 註冊客戶端 tick 事件。 */
    fun registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register { client -> onTick(paused = client.isPaused, nowNanos = System.nanoTime()) }
    }

    /**
     * 記錄一個客戶端 tick；未暫停時累加與上一個 tick 之間的時間。
     *
     * @param paused 遊戲目前是否暫停。
     * @param nowNanos 這個 tick 的系統單調時間，單位為奈秒。
     */
    internal fun onTick(paused: Boolean, nowNanos: Long) {
        val last = lastTickNanos
        if (last != null && !paused) elapsedNanos += nowNanos - last
        lastTickNanos = nowNanos
    }

    override fun nowMillis(): Long = elapsedNanos / NANOS_PER_MILLI

    private companion object {
        /** 每毫秒的奈秒數。 */
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
