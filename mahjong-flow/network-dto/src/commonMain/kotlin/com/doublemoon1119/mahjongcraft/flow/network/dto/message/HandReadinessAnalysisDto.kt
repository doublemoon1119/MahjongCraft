package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 玩家目前手牌的等待牌與整體狀態，不包含任何假想捨牌。
 *
 * @property ruleModuleId 提供分析結果的規則模組識別碼。
 * @property waitingTiles 目前手牌可等待的牌及其可用數量。
 * @property statusIndicatorId 規則提供的狀態指示識別碼；沒有時為 `null`。
 */
@Serializable
data class HandReadinessAnalysisDto(
    val ruleModuleId: String,
    val waitingTiles: List<WaitingTileAvailabilityDto>,
    val statusIndicatorId: String?,
)
