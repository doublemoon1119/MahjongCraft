package com.doublemoon1119.mahjongcraft.platform.fabric.server.concurrency

import kotlin.time.Duration

/** 在伺服器主執行緒上直接處理排給主執行緒的協程工作。 */
fun interface MainThreadDrain {
    /**
     * 在呼叫執行緒持續處理工作，直到 [isDone] 為 true 或超過 [timeout]；只能在伺服器主執行緒呼叫。
     *
     * @param isDone 完成條件；每處理一批工作後檢查。
     * @param timeout 最長等待時間。
     * @return 在時限內達成完成條件時為 true。
     */
    fun drainUntil(isDone: () -> Boolean, timeout: Duration): Boolean
}
