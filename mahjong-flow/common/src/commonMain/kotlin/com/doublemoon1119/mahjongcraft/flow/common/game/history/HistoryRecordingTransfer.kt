package com.doublemoon1119.mahjongcraft.flow.common.game.history

import kotlin.uuid.Uuid

/**
 * 尚未完成的隔離權威歷史轉移；不代表目前存在可供玩家操作的對局。
 *
 * @property venueId 事件來源的場地識別碼。
 * @property lastAcceptedBatch 最近接收的完整交易批次，供內容一致的重試確認使用。
 * @property matchCompleted 是否已接收整場完成事實。
 */
data class HistoryRecordingTransfer(
    val venueId: Uuid,
    val lastAcceptedBatch: List<HistoryOutboxEvent> = emptyList(),
    val matchCompleted: Boolean = false,
)
