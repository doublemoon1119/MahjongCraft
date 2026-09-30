package com.doublemoon1119.mahjongcraft.flow.common.game.history

import kotlin.uuid.Uuid

/** 歷史儲存端已持久化 tombstone 的清理證明。
 *
 * @property matchId 已清理的場次 UUID。
 * @property prunedAtEpochMillis tombstone 建立的 UTC 毫秒時間戳。
 * @property reason 儲存端提供的清理原因。
 */
data class HistoryPruningConfirmation(
    val matchId: Uuid,
    val prunedAtEpochMillis: Long,
    val reason: String,
) {
    init {
        require(prunedAtEpochMillis >= 0L) { "Pruning timestamp must not be negative" }
        require(reason.isNotBlank()) { "Pruning reason must not be blank" }
    }
}
