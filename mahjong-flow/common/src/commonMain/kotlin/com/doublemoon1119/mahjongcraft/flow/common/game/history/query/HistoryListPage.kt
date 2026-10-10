package com.doublemoon1119.mahjongcraft.flow.common.game.history.query

/**
 * 歷史清單查詢成功結果。
 *
 * @property entries 本頁對局摘要。
 * @property nextCursor 下一頁游標。
 */
data class HistoryListPage(
    val entries: List<HistoryMatchSummary>,
    val nextCursor: HistoryQueryCursor?,
)
