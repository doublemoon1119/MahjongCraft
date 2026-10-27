package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import kotlin.uuid.Uuid

/**
 * 收集桌子載入後需要重新建立規則桌面物件的請求。
 *
 * 請求只保存桌子 UUID，不直接呼叫呈現器；消費者在伺服器主執行緒確認位置與搜尋範圍可用後，
 * 才向呈現器要求最新桌況。相同 session 內同一桌只保留一筆待處理請求。
 */
class TablePropRefreshQueue {
    /** 目前接收請求的伺服器 session 與其待處理桌子。 */
    private var active: Session? = null

    /**
     * 開始接收新的伺服器 session 請求。
     *
     * @param server 要接收請求的伺服器。
     */
    @Synchronized
    fun startSession(server: Any) {
        active = Session(server)
    }

    /**
     * 停止接收指定 session 的請求並清除尚未消費的桌子。
     *
     * @param server 要停止的伺服器。
     */
    @Synchronized
    fun stopSession(server: Any) {
        if (active?.server === server) active = null
    }

    /**
     * 登記桌子需要重新呈現規則桌面物件。
     *
     * @param server 產生請求的伺服器。
     * @param tableId 需要重新呈現的桌子 UUID。
     */
    @Synchronized
    fun enqueue(server: Any, tableId: Uuid) {
        active?.takeIf { it.server === server }?.pending?.add(tableId)
    }

    /**
     * 取出目前 session 的待處理桌子，並合併重複請求。
     *
     * @param server 要消費請求的伺服器。
     * @return 依登記順序排列的桌子 UUID。
     */
    @Synchronized
    fun drain(server: Any): List<Uuid> {
        val session = active?.takeIf { it.server === server } ?: return emptyList()
        return session.pending.toList().also { session.pending.clear() }
    }

    /**
     * 查詢目前 session 是否有待處理請求。
     *
     * @param server 要查詢的伺服器。
     * @return 有待處理桌子時為 `true`。
     */
    @Synchronized
    fun hasPending(server: Any): Boolean = active?.let { it.server === server && it.pending.isNotEmpty() } == true

    /**
     * 一次伺服器 session 的請求狀態。
     *
     * @property server 用身分比對的 session token。
     * @property pending 已合併重複 UUID 的待刷新桌子。
     */
    private class Session(
        val server: Any,
        val pending: LinkedHashSet<Uuid> = linkedSetOf(),
    )
}
