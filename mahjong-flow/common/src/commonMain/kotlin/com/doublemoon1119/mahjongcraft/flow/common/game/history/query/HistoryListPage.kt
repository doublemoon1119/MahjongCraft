package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/**
 * 歷史清單查詢成功結果。
 *
 * @property entries 本頁對局摘要。
 * @property nextCursor 從本頁最後一筆往後取下一頁的游標；沒有下一頁時為 null。
 * @property previousCursor 從本頁第一筆往前取上一頁的游標；沒有上一頁時為 null。
 * @property firstEntryIndex 本頁第一筆在完整查詢結果中的位置，從 1 開始；空頁為 0。
 * @property totalCount 符合條件的對局總數。
 */
data class HistoryListPage(
    val entries: List<HistoryMatchSummary>,
    val nextCursor: HistoryQueryCursor?,
    val previousCursor: HistoryQueryCursor?,
    val totalCount: Int,
    val firstEntryIndex: Int = if (entries.isEmpty()) 0 else 1,
)
