package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingDecision
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryArchiveStatusDto
import kotlin.uuid.Uuid

/** 近期已結束場次的最小查詢身分證據，不保存牌局內容。
 *
 * @property participantIds 已結束場次的參與者 UUID。
 * @property decision 已固定的歷史記錄決策；缺少證據時為 null，不猜測排除原因。
 */
internal data class RecentHistoryMatchEvidence(
    val participantIds: Set<Uuid>,
    val decision: HistoryRecordingDecision?,
)

/** SQLite 與權威 outbox 合併後的單場保存證據。
 *
 * @property saved 是否已存在完整 Replay。
 * @property pruned 是否已由清理墓碑證明該場被移除。
 * @property pendingSqlEvents 是否仍有尚未封存的 SQLite 事件。
 * @property failed 是否有已確認的序號缺口或停止原因。
 * @property participantIds 已保存的參與者身分。
 */
internal data class HistoryArchiveStatusEvidence(
    val saved: Boolean,
    val pruned: Boolean,
    val pendingSqlEvents: Boolean,
    val failed: Boolean,
    val participantIds: Set<String>,
)

/** 權威記錄狀態與 SQLite 證據的安全對外判定。
 *
 * @param decision 權威狀態保存的該場記錄決策；若已被清理則可為 null。
 * @param pendingOutbox 是否仍有權威待寫事件或進行中的轉移。
 * @param queryEnabled 目前伺服器是否允許歷史查詢。
 * @param authorized 呼叫者是否有權查詢該場。
 * @param active 是否仍存在權威 Game，或該場尚在權威轉移流程中而不可公開。
 * @return 可安全傳輸的保存狀態。
 */
internal fun HistoryArchiveStatusEvidence.toDto(
    decision: HistoryRecordingDecision?,
    pendingOutbox: Boolean,
    queryEnabled: Boolean,
    authorized: Boolean,
    active: Boolean,
): HistoryArchiveStatusDto = when {
    !queryEnabled -> HistoryArchiveStatusDto.DISABLED
    !authorized -> HistoryArchiveStatusDto.DENIED
    decision == HistoryRecordingDecision.EXCLUDED_CONFIG_DISABLED ||
        decision == HistoryRecordingDecision.STOPPED_CONFIG_DISABLED -> HistoryArchiveStatusDto.DISABLED
    decision == HistoryRecordingDecision.EXCLUDED_AI ||
        decision == HistoryRecordingDecision.EXCLUDED_NO_OPENING -> HistoryArchiveStatusDto.EXCLUDED
    decision == HistoryRecordingDecision.STOPPED_STORAGE_UNAVAILABLE ||
        decision == HistoryRecordingDecision.STOPPED_TRANSFER_INTERRUPTED ||
        failed -> HistoryArchiveStatusDto.FAILED
    active -> HistoryArchiveStatusDto.PENDING
    saved -> HistoryArchiveStatusDto.SAVED
    pruned -> HistoryArchiveStatusDto.MISSING
    pendingOutbox || pendingSqlEvents -> HistoryArchiveStatusDto.PENDING
    else -> HistoryArchiveStatusDto.MISSING
}
