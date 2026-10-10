package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 單局事件頁的 C2S 請求。
 *
 * @property requestId 配對非同步回覆的請求識別碼。
 * @property matchId 對局識別碼。
 * @property scope 查詢範圍。
 * @property roundNumber 局序號。
 * @property startTransactionIndex 事件頁起始交易索引。
 * @property limit 要求的交易數量。
 */
@Serializable
data class HistoryRoundEventsRequestDto(
    val requestId: String,
    val matchId: String,
    val scope: HistoryQueryScopeDto,
    val roundNumber: Int,
    val startTransactionIndex: Int,
    val limit: Int,
)
