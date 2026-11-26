package com.doublemoon1119.mahjongcraft.flow.common.game.history.query
import kotlin.uuid.Uuid

/**
 * 不透明但可驗證的歷史清單游標內容。
 *
 * @property sortValue 上一頁最後一筆的排序值。
 * @property matchId 上一頁最後一筆的 UUID。
 * @property scope 產生游標時使用的查詢範圍。
 * @property sortField 產生游標時使用的排序欄位。
 * @property sortDirection 產生游標時使用的排序方向。
 * @property filters 產生游標時使用的篩選條件。
 */
data class HistoryQueryCursor(
    val sortValue: HistorySortValue,
    val matchId: Uuid,
    val scope: HistoryQueryScope,
    val sortField: HistorySortField,
    val sortDirection: HistorySortDirection,
    val filters: HistoryQueryFilters,
)
