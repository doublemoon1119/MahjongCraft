package com.doublemoon1119.mahjongcraft.logic.judgment

import kotlin.uuid.Uuid

/**
 * 假想捨出 [discardTileId] 後的聽牌分析結果。
 *
 * @property discardTileId 假想捨出的實體牌 ID。
 * @property waitingTiles 捨牌後各等待牌的可用張數與和牌資格。
 * @property statusIndicatorId 分析結果的狀態識別碼；沒有額外狀態時為 null。
 */
data class DiscardReadinessAnalysis(
    val discardTileId: Uuid,
    val waitingTiles: List<WaitingTileAvailability>,
    val statusIndicatorId: String?,
)
