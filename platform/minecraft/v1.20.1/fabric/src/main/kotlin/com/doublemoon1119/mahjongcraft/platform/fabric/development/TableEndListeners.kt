package com.doublemoon1119.mahjongcraft.platform.fabric.development

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.uuid.Uuid

/** 登記的 [TableEndListener]。 */
class TableEndListeners {
    /** 依登記順序通知的接收者。 */
    private val listeners = CopyOnWriteArrayList<TableEndListener>()

    /**
     * 登記一個接收者。
     *
     * @param listener 要登記的接收者。
     */
    fun register(listener: TableEndListener) {
        listeners += listener
    }

    /**
     * 依登記順序通知所有接收者。
     *
     * @param tableId 對局結束或被移除的牌桌。
     */
    fun notifyTableEnded(tableId: Uuid) {
        listeners.forEach { listener -> listener.onTableEnded(tableId) }
    }
}
