package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import kotlin.uuid.Uuid

/** 接收保存或讀取權威存檔時，因內容無法轉換而略過、只留下序號缺口的歷史事件。 */
fun interface SkippedHistoryEventReporter {
    /**
     * 一筆待寫歷史事件無法轉換，已略過並標記序號缺口。
     *
     * @param matchId 事件所屬的場次。
     * @param sequence 被略過的事件序號。
     * @param failure 轉換時丟出的失敗。
     */
    fun onSkipped(matchId: Uuid, sequence: Long, failure: Throwable)
}
