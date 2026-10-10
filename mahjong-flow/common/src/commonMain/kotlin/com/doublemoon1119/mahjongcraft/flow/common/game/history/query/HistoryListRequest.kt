package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/**
 * 歷史清單查詢要求。
 *
 * @property scope 查詢範圍。
 * @property sortField 排序欄位。
 * @property sortDirection 排序方向。
 * @property filters 條件篩選。
 * @property pageSize 每頁筆數。
 * @property cursor 延續上一頁的游標。
 */
data class HistoryListRequest(
    val scope: HistoryQueryScope = HistoryQueryScope.OWN,
    val sortField: HistorySortField = HistorySortField.ENDED_AT,
    val sortDirection: HistorySortDirection = HistorySortDirection.DESC,
    val filters: HistoryQueryFilters = HistoryQueryFilters(),
    val pageSize: Int = HistoryQueryLimits.DEFAULT_PAGE_SIZE,
    val cursor: HistoryQueryCursor? = null,
) {
    init {
        require(pageSize in HistoryQueryLimits.MIN_PAGE_SIZE..HistoryQueryLimits.MAX_PAGE_SIZE) {
            "History page size must be between ${HistoryQueryLimits.MIN_PAGE_SIZE} and ${HistoryQueryLimits.MAX_PAGE_SIZE}"
        }
    }
}
