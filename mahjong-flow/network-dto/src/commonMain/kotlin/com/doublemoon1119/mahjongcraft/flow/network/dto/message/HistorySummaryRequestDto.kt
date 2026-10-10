package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 單場歷史摘要查詢的 C2S 請求。
 *
 *
 * @property requestId 配對非同步回覆的請求識別碼。
 * @property matchId 要查詢的對局識別碼。
 * @property scope 要查詢的資料範圍。
 */
@Serializable
data class HistorySummaryRequestDto(
    val requestId: String,
    val matchId: String,
    val scope: HistoryQueryScopeDto,
)
