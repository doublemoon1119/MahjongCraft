package com.doublemoon1119.mahjongcraft.flow.common.game.history.query
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundPosition
import kotlin.uuid.Uuid

/**
 * 歷史單局桌況查詢要求。
 *
 * @property matchId 欲查詢的對局 UUID。
 * @property scope 查詢範圍。
 * @property roundNumber 欲讀取的局序號。
 * @property position 欲重建的局內位置。
 */
data class HistoryRoundStateRequest(
    val matchId: Uuid,
    val scope: HistoryQueryScope = HistoryQueryScope.OWN,
    val roundNumber: Int,
    val position: HistoryRoundPosition = HistoryRoundPosition.Initial,
)
