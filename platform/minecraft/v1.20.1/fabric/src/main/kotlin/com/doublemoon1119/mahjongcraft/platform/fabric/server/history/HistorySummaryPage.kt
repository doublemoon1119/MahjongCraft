package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

/**
 * 以結束時間與 matchId 排序的摘要游標，紀錄被清理後仍可依值接續。
 *
 * @property endedAtEpochMillis 上頁最後一筆的結束時間。
 * @property matchId 上頁最後一筆的穩定識別碼。
 */
internal data class HistorySummaryCursor(val endedAtEpochMillis: Long, val matchId: String)

/**
 * 伺服器內部已保存場次摘要，不含玩家或牌面內容。
 *
 * @property matchId 對局識別碼。
 * @property state 完整或已確認部分狀態。
 * @property startedAtEpochMillis 可證實的開局時間；無證據時為 null。
 * @property endedAtEpochMillis 結束或權威確認終止時間。
 * @property ruleId 可取得的規則識別碼；未知時為 null。
 * @property tableId 可取得的牌桌識別碼。
 * @property dimensionId 可取得的維度識別碼；未知時為 null。
 */
internal data class HistoryStoredMatchSummary(
    val matchId: String,
    val state: HistoryStoredMatchState,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long,
    val ruleId: String?,
    val tableId: String?,
    val dimensionId: String?,
)

/**
 * 有界的摘要分頁。
 *
 * @property entries 依結束時間與 matchId 升冪排列的場次摘要。
 * @property nextCursor 尚有下一頁時的游標，否則為 null。
 */
internal data class HistorySummaryPage(val entries: List<HistoryStoredMatchSummary>, val nextCursor: HistorySummaryCursor?)
