package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * Replay 玩家識別資料 DTO。
 *
 * @property initialSeatIndex 初始座位索引。
 * @property playerId 玩家識別碼。
 * @property aiStrategyId AI 策略識別碼。
 */
@Serializable
data class HistoryReplayPlayerIdentityDto(
    val initialSeatIndex: Int,
    val playerId: String?,
    val aiStrategyId: String?,
)
