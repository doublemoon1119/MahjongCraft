package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * 唯讀清理預覽，將確定政策候選與可能的容量追加候選分開。
 *
 * @property countsByReason 確定清理候選的主要原因與場數。
 * @property logicalBytes 確定候選的 payload 位元組數，不等於磁碟回收量。
 * @property disk 目前實際磁碟大小。
 * @property additionalCandidateCount 容量超標時可能追加的候選場數，不保證全部刪除。
 * @property additionalLogicalBytes 可能追加候選的 payload 大小。
 * @property policy 評估所用固定政策。
 * @property evaluatedAt 評估 UTC 時刻。
 */
internal data class HistoryCleanupPreview(
    val countsByReason: Map<HistoryCleanupReason, Long>,
    val logicalBytes: Long,
    val disk: HistoryDiskUsage,
    val additionalCandidateCount: Long,
    val additionalLogicalBytes: Long,
    val policy: HistoryRetentionPolicy,
    val evaluatedAt: Instant,
)

/**
 * 已完成或部分完成的管理清理結果。
 *
 * @property removedMatches 此次工作已確認提交的場數。
 * @property diskBefore 執行前大小；量測失敗時為 null。
 * @property diskAfter 執行後大小；量測失敗時為 null。
 * @property storageAvailable 儲存端是否可接受新增資料。
 * @property recoveryBusy 是否因 SQLite 連線占用延後回收；尚未完成檢查時為 null。
 * @property incrementalSupported 是否支援漸進回收；尚未檢查時為 null。
 * @property completed 是否走完維護流程；回收延後及容量不足另由對應欄位表示。
 */
internal data class HistoryCleanupReport(
    val removedMatches: Long,
    val diskBefore: HistoryDiskUsage?,
    val diskAfter: HistoryDiskUsage?,
    val storageAvailable: Boolean,
    val recoveryBusy: Boolean?,
    val incrementalSupported: Boolean?,
    val completed: Boolean,
)

/** 不洩漏原始例外或路徑的 session 管理操作結果。 */
internal sealed interface HistoryManagementResult<out T> {
    /**
     * 已取得此 session 的結果。
     *
     * @property value 型別化管理結果。
     * @property sessionId 產生結果的 session，回覆前須再次驗證。
     */
    data class Success<T>(val value: T, val sessionId: Uuid) : HistoryManagementResult<T>

    /**
     * 另一項管理工作仍在執行，不新增等待工作。
     *
     * @property cachedSnapshot 可選的舊用量快照，必須明示非即時值。
     */
    data class Busy(val cachedSnapshot: HistoryStorageSnapshot?) : HistoryManagementResult<Nothing>

    /**
     * 目前未連線，不自動重新開庫。
     *
     * @property cachedSnapshot 同一 session 的可選舊快照。
     */
    data class Disconnected(val cachedSnapshot: HistoryStorageSnapshot?) : HistoryManagementResult<Nothing>

    /** 原請求所屬 session 已失效，不跨存檔執行或回覆。 */
    data object SessionChanged : HistoryManagementResult<Nothing>

    /**
     * 管理工作失敗，原始原因只寫入伺服器 log。
     *
     * @property cleanup 已確認的部分清理結果，不將已提交刪除報成未變動。
     * @property cachedSnapshot 失敗前的可選舊快照。
     */
    data class Failed(val cleanup: HistoryCleanupReport? = null, val cachedSnapshot: HistoryStorageSnapshot? = null) : HistoryManagementResult<Nothing>
}
