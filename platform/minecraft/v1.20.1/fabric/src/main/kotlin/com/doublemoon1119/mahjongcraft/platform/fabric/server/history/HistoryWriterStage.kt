package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

/** 歷史寫入背景工作中分別量測耗時的環節。 */
enum class HistoryWriterStage {
    /** 量測資料庫檔案的磁碟用量。 */
    DISK_USAGE,

    /** 同步清理收據、場次結束時間、停止原因與序號缺口。 */
    DECISION_SYNC,

    /** 讀取已清理場次的墓碑。 */
    TOMBSTONES,

    /** 把一批待寫事件編碼成持久化格式。 */
    ENCODE,

    /** 以一筆交易把一批事件寫進資料庫。 */
    BATCH_WRITE,

    /** 待寫佇列清空後，對帳並把已結束的場次整理成完整 Replay。 */
    ARCHIVE,

    /** 定期執行的對帳、封存與保留政策清理。 */
    MAINTENANCE,

    /** 定期重新統計儲存用量。 */
    STORAGE_STATS,
}
