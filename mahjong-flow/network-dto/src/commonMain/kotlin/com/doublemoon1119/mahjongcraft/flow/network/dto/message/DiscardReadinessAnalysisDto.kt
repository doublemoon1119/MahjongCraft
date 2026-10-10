package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 打出指定實體手牌後的聽牌分析。
 *
 * @property discardTileId 要分析的捨牌實體 UUID 字串。
 * @property waitingTiles 打出該牌後可等待的牌及其可用數量。
 * @property statusIndicatorId 規則提供的狀態指示識別碼；沒有時為 `null`。
 */
@Serializable
data class DiscardReadinessAnalysisDto(
    val discardTileId: String,
    val waitingTiles: List<WaitingTileAvailabilityDto>,
    val statusIndicatorId: String?,
)
