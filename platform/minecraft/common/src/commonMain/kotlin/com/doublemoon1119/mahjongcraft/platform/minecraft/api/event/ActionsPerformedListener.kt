package com.doublemoon1119.mahjongcraft.platform.minecraft.api.event

import com.doublemoon1119.mahjongcraft.flow.api.event.ActionsPerformedEvent

/** 一筆交易中成立動作的訂閱者。 */
fun interface ActionsPerformedListener {
    /**
     * 處理一筆交易中依成立順序整理的動作。
     *
     * @param event 成立動作事件。
     * @param context 事件產生時的情境。
     */
    fun onActionsPerformed(event: ActionsPerformedEvent, context: GameEventContext)
}
