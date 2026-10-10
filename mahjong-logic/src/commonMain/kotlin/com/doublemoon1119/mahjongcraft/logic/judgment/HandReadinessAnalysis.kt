package com.doublemoon1119.mahjongcraft.logic.judgment

/** 玩家目前手牌的等待牌與整體狀態，不包含任何假想捨牌。 */
data class HandReadinessAnalysis(
    val waitingTiles: List<WaitingTileAvailability>,
    val statusIndicatorId: String?,
)
