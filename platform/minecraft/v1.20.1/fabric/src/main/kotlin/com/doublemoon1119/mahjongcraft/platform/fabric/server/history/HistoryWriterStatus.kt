package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

/**
 * 管理員可見的歷史寫入狀態，不包含資料庫路徑或事件內容。
 *
 * @property databaseConnected 是否已開啟目前世界的資料庫。
 * @property recordingEnabled 有效政策是否允許新對局記錄歷史；不表示每場皆符合資格或資料庫已連線。
 * @property pendingEventCount 權威 outbox 尚待寫入的事件數。
 * @property knownGapCount 已知具有序號缺口的場次數。
 * @property lastError 最近是否發生錯誤；原始文字只供內部診斷，不傳給玩家。
 * @property storagePaused 是否因實際容量不足或量測失敗暫停新增歷史，與有效總開關分離。
 */
data class HistoryWriterStatus(
    val databaseConnected: Boolean,
    val recordingEnabled: Boolean,
    val pendingEventCount: Int,
    val knownGapCount: Int,
    val lastError: String?,
    val storagePaused: Boolean = false,
)
