package com.doublemoon1119.mahjongcraft.flow.common.game.history.query
import kotlin.uuid.Uuid

/**
 * 歷史對局中可公開的終局結果。
 *
 * @property playerId 玩家 UUID。
 * @property finalScore 終局分數。
 * @property finalRank 依規則比較器計算的終局名次。
 */
data class HistoryResultSummary(
    val playerId: Uuid,
    val finalScore: Int?,
    val finalRank: Int?,
)
