package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import kotlin.uuid.Uuid

/** 歷史記錄因磁碟用量上限而暫停或恢復的狀態變化。 */
internal enum class HistoryDiskLimitTransition {
    /** 用量超過上限，開始暫停記錄。 */
    PAUSED,

    /** 用量回到上限內，恢復記錄。 */
    RESUMED,
}

/**
 * 追蹤清理後的磁碟用量是否在上限內，只在狀態改變時回報，讓 log 不會每次維護都重複。
 *
 * 狀態屬於單一存檔 session：換到新的 session 時視為尚未暫停，因此新世界第一次超過上限一定會回報。
 */
internal class HistoryDiskLimitTracker {
    /** 目前狀態所屬的存檔 session；尚未記錄過時為 null。 */
    private var sessionId: Uuid? = null

    /** 這個 session 目前是否因磁碟用量上限而暫停記錄。 */
    private var paused = false

    /**
     * 記錄一次清理後的結果。
     *
     * @param session 這次清理所屬的存檔 session。
     * @param storageAvailable 清理後用量是否在上限內。
     * @return 狀態改變時的變化；沒有改變時為 null。
     */
    @Synchronized
    fun update(session: Uuid, storageAvailable: Boolean): HistoryDiskLimitTransition? {
        if (session != sessionId) {
            sessionId = session
            paused = false
        }
        val nowPaused = !storageAvailable
        if (nowPaused == paused) return null
        paused = nowPaused
        return if (nowPaused) HistoryDiskLimitTransition.PAUSED else HistoryDiskLimitTransition.RESUMED
    }
}
