package com.doublemoon1119.mahjongcraft.platform.minecraft.event

/**
 * 將工作排入平台的主執行緒佇列。
 *
 * 實作不得在 `enqueue` 內直接執行工作；即使呼叫端已在主執行緒，也必須讓工作經由平台佇列稍後執行。
 */
fun interface GameEventScheduler {
    /**
     * 排入一個稍後執行的事件交付工作。
     *
     * @param task 要在平台主執行緒稍後執行的工作。
     */
    fun enqueue(task: () -> Unit)
}
