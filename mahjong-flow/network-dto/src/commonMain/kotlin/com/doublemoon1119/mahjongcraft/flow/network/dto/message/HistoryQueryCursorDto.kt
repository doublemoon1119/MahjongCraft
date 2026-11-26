package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 可驗證的歷史 keyset cursor 傳輸內容。
 *
 *
 * @property numericSortValue 上一頁最後一筆的數值排序值。
 * @property nullBucket 上一頁最後一筆是否位於 null 排序桶。
 * @property matchId 上一頁最後一筆的對局 UUID 字串。
 * @property scope 產生 cursor 時使用的查詢範圍。
 * @property sortField 產生 cursor 時使用的排序欄位。
 * @property sortDirection 產生 cursor 時使用的排序方向。
 * @property filters 產生 cursor 時使用的完整篩選條件。
 */
@Serializable
data class HistoryQueryCursorDto(
    val numericSortValue: Long?,
    val nullBucket: Boolean,
    val matchId: String,
    val scope: HistoryQueryScopeDto,
    val sortField: HistorySortFieldDto,
    val sortDirection: HistorySortDirectionDto,
    val filters: HistoryQueryFiltersDto,
)
