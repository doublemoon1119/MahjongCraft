package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history

/**
 * 一次磁碟回收操作的結果。
 *
 * @property busy SQLite 是否因連線佔用而未完成部分回收。
 * @property incrementalSupported 目前資料庫是否支援 incremental vacuum。
 */
internal data class HistoryDiskRecovery(
    val busy: Boolean,
    val incrementalSupported: Boolean,
)
