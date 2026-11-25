package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history

/**
 * 歷史資料庫及其 SQLite sidecar 的磁碟用量。
 *
 * @property dbBytes 主資料庫檔案大小。
 * @property walBytes WAL sidecar 檔案大小。
 * @property shmBytes shared-memory sidecar 檔案大小。
 */
data class HistoryDiskUsage(
    val dbBytes: Long,
    val walBytes: Long,
    val shmBytes: Long,
) {
    /** 三個檔案大小的安全總和。 */
    val totalBytes: Long get() = Math.addExact(Math.addExact(dbBytes, walBytes), shmBytes)
}
