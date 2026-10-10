package com.doublemoon1119.mahjongcraft.flow.common.game.history.query
import kotlin.uuid.Uuid

/**
 * 歷史單局事件查詢要求。
 *
 * @property matchId 欲查詢的對局 UUID。
 * @property scope 查詢範圍。
 * @property roundNumber 欲讀取的局序號。
 * @property startTransactionIndex 事件頁的起始交易索引。
 * @property limit 要求的交易數量。
 */
data class HistoryRoundEventsRequest(
    val matchId: Uuid,
    val scope: HistoryQueryScope = HistoryQueryScope.OWN,
    val roundNumber: Int,
    val startTransactionIndex: Int = 0,
    val limit: Int = HistoryQueryLimits.DEFAULT_REPLAY_PAGE_SIZE,
)
