package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

/**
 * 指定批次場次的儲存證據，不載入事件或 Replay 內容。
 *
 * @property replayBytes 尚存在完整 Replay 時的 UTF-8 大小；尚未封存或已清理時為 null。
 * @property pruned 是否存在已提交的清理收據。
 * @property disk 同一查詢工作取得的實際磁碟大小。
 */
internal data class HistoryGenerationReceipt(
    val replayBytes: Map<String, Long>,
    val pruned: Set<String>,
    val disk: HistoryDiskUsage,
)
