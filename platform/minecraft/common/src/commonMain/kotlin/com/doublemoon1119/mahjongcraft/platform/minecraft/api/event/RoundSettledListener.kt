package com.doublemoon1119.mahjongcraft.platform.minecraft.api.event

import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettledEvent

/** 一局結算事件的訂閱者。 */
fun interface RoundSettledListener {
    /**
     * 處理一局結算事件。
     *
     * @param event 一局結算事件。
     * @param context 事件產生時的情境。
     */
    fun onRoundSettled(event: RoundSettledEvent, context: GameEventContext)
}
