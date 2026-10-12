package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * Replay 玩家識別資料 DTO。
 *
 * @property initialSeatIndex 初始座位索引。
 * @property playerId 玩家識別碼；此 DTO 型別允許 null，但目前的 Replay 投影會提供識別碼，領域模型不允許缺少此值。
 * @property aiStrategyId AI 策略識別碼；未記錄 AI 策略時為 null。
 */
@Serializable
data class HistoryReplayPlayerIdentityDto(
    val initialSeatIndex: Int,
    val playerId: String?,
    val aiStrategyId: String?,
)
