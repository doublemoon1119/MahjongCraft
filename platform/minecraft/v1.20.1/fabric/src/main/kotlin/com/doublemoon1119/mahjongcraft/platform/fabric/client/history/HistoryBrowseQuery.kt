package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryPageDirectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryFiltersDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryQueryScopeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortDirectionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySortFieldDto

/**
 * 客戶端歷史瀏覽清單目前使用的查詢條件。
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
    val filters: HistoryQueryFiltersDto = HistoryQueryFiltersDto.NONE,
) {
    /**
     * 將目前條件調整為指定資料範圍可用的形式。
     *
     * @return 清除不適用名次條件並修正排序欄位後的查詢條件。
     */
    fun normalized(): HistoryBrowseQuery {
        if (!scope.coversAllMatches) return this
        val normalizedSortField = when (sortField) {
            HistorySortFieldDto.OWN_RANK, HistorySortFieldDto.OWN_SCORE -> HistorySortFieldDto.ENDED_AT
            else -> sortField
        }
        return copy(
            sortField = normalizedSortField,
            filters = filters.copy(ownRankMin = null, ownRankMax = null),
        )
    }

    /**
     * 將查詢條件轉成指定游標與方向的線路請求。
     *
     * @param cursor 回覆提供的不透明游標；沒有游標時從第一筆或最後一筆開始。
     * @param direction 從游標往哪一邊取一頁。
     * @return 使用固定頁大小及空配對識別碼的歷史清單請求。
     */
    fun toRequest(cursor: String?, direction: HistoryPageDirectionDto): HistoryListRequestDto = HistoryListRequestDto(
        requestId = "",
        scope = scope,
        sortField = sortField,
        sortDirection = sortDirection,
        filters = filters,
        pageSize = 20,
        cursor = cursor,
        direction = direction,
    )
}

/** 是否查詢不限玩家本人的全部對局；全部對局與壓力測試資料都沒有個人名次與個人分數。 */
internal val HistoryQueryScopeDto.coversAllMatches: Boolean
    get() = this != HistoryQueryScopeDto.OWN
