package com.doublemoon1119.mahjongcraft.flow.common.game.history

/** 有界歷史轉移的追加結果；等待容量不會產生事件缺口。 */
enum class HistoryTransferResult {
    /** 已接收批次，或確認內容完全相同的最近批次。 */
    ACCEPTED,

    /** 須先等待儲存端排空待寫事件。 */
    WAITING_FOR_CAPACITY,

    /** 固定資格或儲存政策已停止該場轉移。 */
    STOPPED,
}
