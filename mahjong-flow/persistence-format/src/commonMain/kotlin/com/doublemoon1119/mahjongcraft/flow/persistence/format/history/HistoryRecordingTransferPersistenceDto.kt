package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import kotlinx.serialization.Serializable

/**
 * 未完成歷史轉移的持久化 metadata。
 *
 * @property venueId 來源場地 UUID 字串。
 * @property lastAcceptedBatch 最近確認的完整交易批次。
 * @property matchCompleted 是否已有整場完成事實。
 */
@Serializable
data class HistoryRecordingTransferPersistenceDto(
    val venueId: String,
    val lastAcceptedBatch: List<HistoryOutboxEventPersistenceDto>,
    val matchCompleted: Boolean,
)
