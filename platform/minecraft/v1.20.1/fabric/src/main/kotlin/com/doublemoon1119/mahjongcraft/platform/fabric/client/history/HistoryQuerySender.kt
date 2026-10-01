package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryRequestDto

/** 將歷史查詢 DTO 交給平台網路實作的窄介面。 */
interface HistoryQuerySender {
    /**
     * 傳送已包含唯一配對識別碼的歷史清單要求。
     *
     * @param request 要傳送的歷史清單要求。
     */
    fun sendList(request: HistoryListRequestDto)

    /**
     * 傳送已包含唯一配對識別碼的單場摘要要求。
     *
     * @param request 要傳送的單場摘要要求。
     */
    fun sendSummary(request: HistorySummaryRequestDto)
}
