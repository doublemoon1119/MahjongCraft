package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 單局事件頁的 S2C 回覆。
 *
 * @property requestId 對應請求的識別碼。
 * @property matchId 回應所屬對局識別碼。
 * @property roundNumber 回應所屬局序號。
 * @property startTransactionIndex 回應起始交易索引。
 * @property events 事件頁；內容已通過查詢上限。
 * @property errorCode 安全的查詢錯誤代碼。
 */
@Serializable
data class HistoryRoundEventsResponseDto(
    val requestId: String,
    val matchId: String,
    val roundNumber: Int,
    val startTransactionIndex: Int,
    val events: HistoryRoundEventsDto?,
    val errorCode: HistoryQueryErrorCodeDto?,
)
