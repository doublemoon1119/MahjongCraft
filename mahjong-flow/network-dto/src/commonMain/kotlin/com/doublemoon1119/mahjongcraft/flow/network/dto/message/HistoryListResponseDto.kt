package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 歷史清單的 S2C 回覆。
 *
 *
 * @property requestId 對應請求的識別碼。
 * @property entries 可安全顯示的對局摘要。
 * @property nextCursor 下一頁 keyset cursor；null 表示沒有下一頁。
 * @property errorCode 安全的查詢錯誤代碼。
 * @property allowAll 是否允許查詢全部已保存歷史。
 * @property allowStressTest 是否允許查詢壓力測試資料（[HistoryQueryScopeDto.STRESS_TEST]）。
 */
@Serializable
data class HistoryListResponseDto(
    val requestId: String,
    val entries: List<HistoryMatchSummaryDto>,
    val nextCursor: String?,
    val errorCode: HistoryQueryErrorCodeDto?,
    val allowAll: Boolean,
    val allowStressTest: Boolean,
)
