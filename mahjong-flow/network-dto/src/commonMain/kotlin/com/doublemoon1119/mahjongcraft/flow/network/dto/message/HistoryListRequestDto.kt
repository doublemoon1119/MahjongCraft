package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 歷史清單查詢的 C2S 請求。
 *
 *
 * @property requestId 配對非同步回覆的請求識別碼。
 * @property scope 要查詢的資料範圍。
 * @property sortField 清單排序欄位。
 * @property sortDirection 清單排序方向。
 * @property filters 清單篩選條件。
 * @property pageSize 每頁要求的資料筆數。
 * @property cursor 前一頁回覆提供的不透明 keyset cursor。
 */
@Serializable
data class HistoryListRequestDto(
    val requestId: String,
    val scope: HistoryQueryScopeDto,
    val sortField: HistorySortFieldDto,
    val sortDirection: HistorySortDirectionDto,
    val filters: HistoryQueryFiltersDto,
    val pageSize: Int,
    val cursor: String?,
)
