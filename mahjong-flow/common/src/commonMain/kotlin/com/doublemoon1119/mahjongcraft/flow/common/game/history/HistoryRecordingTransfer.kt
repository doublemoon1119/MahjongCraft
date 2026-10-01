package com.doublemoon1119.mahjongcraft.flow.common.game.history

import kotlin.uuid.Uuid

/**
 * 尚未完成的隔離權威歷史轉移；不代表目前存在可供玩家操作的對局。
 *
 * @property tableId 事件來源的牌桌識別碼。
 * @property lastAcceptedBatch 最近接收的完整交易批次，供內容一致的重試確認使用。
 * @property matchCompleted 是否已接收整場完成事實。
 */
data class HistoryRecordingTransfer(
    val tableId: Uuid,
    val lastAcceptedBatch: List<HistoryOutboxEvent> = emptyList(),
    val matchCompleted: Boolean = false,
)

/** 有界歷史轉移的追加結果；等待容量不會產生事件缺口。 */
enum class HistoryTransferResult {
    /** 已接收批次，或確認內容完全相同的最近批次。 */
    ACCEPTED,

    /** 須先等待儲存端排空待寫事件。 */
    WAITING_FOR_CAPACITY,

    /** 固定資格或儲存政策已停止該場轉移。 */
    STOPPED,
}
