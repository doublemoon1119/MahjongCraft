package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import kotlin.time.Duration

/** 接收歷史寫入背景工作各環節的耗時與寫入事件數，供壓力測試等量測使用；可能在背景執行緒上呼叫。 */
interface HistoryWriterObserver {
    /**
     * 完成一個環節。
     *
     * @param stage 環節。
     * @param duration 耗時。
     */
    fun onStage(stage: HistoryWriterStage, duration: Duration)

    /**
     * 一批事件已寫入並從待寫佇列確認移除。
     *
     * @param count 事件數。
     */
    fun onEventsWritten(count: Int)
}
