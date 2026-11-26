package com.doublemoon1119.mahjongcraft.flow.server.state

import kotlin.time.Duration

/** 接收權威交易中記錄歷史事件的耗時與新增事件數，供壓力測試等量測使用。 */
fun interface HistoryRecordingObserver {
    /**
     * 一次帶有歷史事件的交易完成記錄。
     *
     * 在 [AuthoritativeStateStore.update] 的交易內、持有互斥鎖時呼叫，因此實作不可再存取同一個儲存。
     *
     * @param duration 比對桌況變化並把事件加入待寫佇列的耗時。
     * @param appendedEvents 新加入待寫佇列的事件數。
     */
    fun onHistoryRecorded(duration: Duration, appendedEvents: Int)
}
