package com.doublemoon1119.mahjongcraft.flow.common.game.history.query
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundPosition
import kotlin.uuid.Uuid

/**
 * 歷史單局桌況查詢要求：從指定位置起連續幾個位置的桌況。
 *
 * @property matchId 欲查詢的對局 UUID。
 * @property scope 查詢範圍。
 * @property roundNumber 欲讀取的局序號。
 * @property position 第一個要重建的局內位置。
 * @property count 最多幾個位置，介於 1 與 [HistoryQueryLimits.MAX_ROUND_STATES] 之間。
 */
data class HistoryRoundStateRequest(
    val matchId: Uuid,
    val scope: HistoryQueryScope = HistoryQueryScope.OWN,
    val roundNumber: Int,
    val position: HistoryRoundPosition = HistoryRoundPosition.Initial,
    val count: Int = 1,
)
