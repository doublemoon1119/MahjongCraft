package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 單場歷史摘要查詢的 S2C 回覆。
 *
 *
 * @property requestId 對應請求的識別碼。
 * @property detail 對局摘要及局級索引；不可提供時為 null。
 * @property errorCode 安全的查詢錯誤代碼。
 */
@Serializable
data class HistorySummaryResponseDto(
    val requestId: String,
    val detail: HistoryMatchDetailDto?,
    val errorCode: HistoryQueryErrorCodeDto?,
)
