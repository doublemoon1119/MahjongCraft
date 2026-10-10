package com.doublemoon1119.mahjongcraft.flow.common.game.history.query
import kotlin.uuid.Uuid

/**
 * 歷史摘要查詢要求。
 *
 * @property matchId 欲查詢的對局 UUID。
 * @property scope 查詢範圍。
 */
data class HistorySummaryRequest(
    val matchId: Uuid,
    val scope: HistoryQueryScope = HistoryQueryScope.OWN,
)
