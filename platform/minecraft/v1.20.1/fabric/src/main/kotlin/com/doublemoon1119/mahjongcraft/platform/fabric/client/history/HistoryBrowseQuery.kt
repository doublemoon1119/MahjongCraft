package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryFiltersDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortDirectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortFieldDto

/** 客戶端歷史瀏覽清單目前使用的查詢條件。
 *
 * @property scope 查詢的資料範圍。
 * @property sortField 清單的排序欄位。
 * @property sortDirection 清單的排序方向。
 * @property filters 清單的複合篩選條件。
 */
internal data class HistoryBrowseQuery(
    val scope: HistoryQueryScopeDto = HistoryQueryScopeDto.OWN,
    val sortField: HistorySortFieldDto = HistorySortFieldDto.ENDED_AT,
    val sortDirection: HistorySortDirectionDto = HistorySortDirectionDto.DESC,
    val filters: HistoryQueryFiltersDto = HistoryQueryFiltersDto(),
) {
    /** 將目前條件調整為指定資料範圍可用的形式。
     *
     * @return 清除不適用名次條件並修正排序欄位後的查詢條件。
     */
    fun normalized(): HistoryBrowseQuery {
        if (scope != HistoryQueryScopeDto.ALL) return this
        val normalizedSortField = when (sortField) {
            HistorySortFieldDto.OWN_RANK, HistorySortFieldDto.OWN_SCORE -> HistorySortFieldDto.ENDED_AT
            else -> sortField
        }
        return copy(
            sortField = normalizedSortField,
            filters = filters.copy(ownRankMin = null, ownRankMax = null),
        )
    }

    /** 將查詢條件轉成第一頁或指定游標的線路請求。
     *
     * @param cursor 前一頁回覆提供的不透明游標，第一頁為 `null`。
     * @return 使用固定頁大小及空配對識別碼的歷史清單請求。
     */
    fun toRequest(cursor: String?): HistoryListRequestDto = HistoryListRequestDto(
        requestId = "",
        scope = scope,
        sortField = sortField,
        sortDirection = sortDirection,
        filters = filters,
        pageSize = 20,
        cursor = cursor,
    )
}
