package com.doublemoon1119.mahjongcraft.logic.judgment

import kotlin.uuid.Uuid

/** 假想捨出 [discardTileId] 後的聽牌分析結果。 */
data class DiscardReadinessAnalysis(
    val discardTileId: Uuid,
    val waitingTiles: List<WaitingTileAvailability>,
    val statusIndicatorId: String?,
)
