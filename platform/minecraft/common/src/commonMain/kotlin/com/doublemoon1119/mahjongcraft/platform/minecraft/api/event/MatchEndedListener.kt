package com.doublemoon1119.mahjongcraft.platform.minecraft.api.event

import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEndedEvent

/** 對局結束事件的訂閱者。 */
fun interface MatchEndedListener {
    /**
     * 處理一場對局結束事件。
     *
     * @param event 對局結束事件。
     * @param context 事件產生時的情境。
     */
    fun onMatchEnded(event: MatchEndedEvent, context: GameEventContext)
}
