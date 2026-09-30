package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

/** 歷史資料庫及其 SQLite sidecar 的磁碟用量。
 *
 * @property dbBytes 主資料庫檔案大小。
 * @property walBytes WAL sidecar 檔案大小。
 * @property shmBytes shared-memory sidecar 檔案大小。
 */
internal data class HistoryDiskUsage(
    val dbBytes: Long,
    val walBytes: Long,
    val shmBytes: Long,
) {
    /** 三個檔案大小的安全總和。 */
    val totalBytes: Long get() = Math.addExact(Math.addExact(dbBytes, walBytes), shmBytes)
}

/** 一次磁碟回收操作的結果。
 *
 * @property busy SQLite 是否因連線佔用而未完成部分回收。
 * @property incrementalSupported 目前資料庫是否支援 incremental vacuum。
 */
internal data class HistoryDiskRecovery(
    val busy: Boolean,
    val incrementalSupported: Boolean,
)
