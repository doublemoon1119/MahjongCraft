package com.doublemoon1119.mahjongcraft.platform.minecraft.api.event

import com.doublemoon1119.mahjongcraft.flow.api.event.MatchStartedEvent

/** 對局開始事件的訂閱者。 */
fun interface MatchStartedListener {
    /**
     * 處理一場對局開始事件。
     *
     * @param event 對局開始事件。
     * @param context 事件產生時的情境。
     */
    fun onMatchStarted(event: MatchStartedEvent, context: GameEventContext)
}
