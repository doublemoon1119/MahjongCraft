package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

/** 歷史記錄因磁碟用量上限而暫停或恢復的狀態變化。 */
internal enum class HistoryDiskLimitTransition {
    /** 用量超過上限，開始暫停記錄。 */
    PAUSED,

    /** 用量回到上限內，恢復記錄。 */
    RESUMED,
}

/** 追蹤清理後的磁碟用量是否在上限內，只在狀態改變時回報，讓 log 不會每次維護都重複。 */
internal class HistoryDiskLimitTracker {
    /** 目前是否因磁碟用量上限而暫停記錄。 */
    private var paused = false

    /**
     * 記錄一次清理後的結果。
     *
     * @param storageAvailable 清理後用量是否在上限內。
     * @return 狀態改變時的變化；沒有改變時為 null。
     */
    @Synchronized
    fun update(storageAvailable: Boolean): HistoryDiskLimitTransition? {
        val nowPaused = !storageAvailable
        if (nowPaused == paused) return null
        paused = nowPaused
        return if (nowPaused) HistoryDiskLimitTransition.PAUSED else HistoryDiskLimitTransition.RESUMED
    }
}
