package com.doublemoon1119.mahjongcraft.platform.minecraft.api.event

import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch

/** 一筆交易的公開事件批次交付完成後的訂閱者。 */
fun interface EventBatchCommittedListener {
    /**
     * 處理同一筆交易內已依序交付的事件批次。
     *
     * @param batch 同一筆交易中的公開事件批次。
     * @param context 事件產生時的情境；批次內所有事件共用同一份情境快照。
     */
    fun onEventBatchCommitted(batch: CommittedMatchEventBatch, context: GameEventContext)
}
