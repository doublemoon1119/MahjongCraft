package com.doublemoon1119.mahjongcraft.platform.minecraft.api.event

import com.doublemoon1119.mahjongcraft.flow.api.event.RoundCompletedEvent

/** 本局正式完成事件的訂閱者。 */
fun interface RoundCompletedListener {
    /**
     * 處理一局完成事件。
     *
     * @param event 本局完成事件。
     * @param context 事件產生時的情境。
     */
    fun onRoundCompleted(event: RoundCompletedEvent, context: GameEventContext)
}
