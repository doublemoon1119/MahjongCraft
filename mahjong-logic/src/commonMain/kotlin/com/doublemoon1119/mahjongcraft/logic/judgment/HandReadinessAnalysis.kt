package com.doublemoon1119.mahjongcraft.logic.judgment

/**
 * 玩家目前手牌的等待牌與整體狀態，不包含任何假想捨牌。
 *
 * @property waitingTiles 目前等待牌的可用張數與和牌資格。
 * @property statusIndicatorId 分析結果的狀態識別碼；沒有額外狀態時為 null。
 */
data class HandReadinessAnalysis(
    val waitingTiles: List<WaitingTileAvailability>,
    val statusIndicatorId: String?,
)
