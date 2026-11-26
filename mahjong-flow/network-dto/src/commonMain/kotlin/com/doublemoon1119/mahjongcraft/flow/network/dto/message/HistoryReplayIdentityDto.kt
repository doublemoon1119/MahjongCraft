package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * Replay 對局識別資料 DTO。
 *
 * @property matchId 對局識別碼。
 * @property venueId 場地識別碼。
 * @property players 依初始座位排序的玩家識別資料。
 */
@Serializable
data class HistoryReplayIdentityDto(
    val matchId: String,
    val venueId: String,
    val players: List<HistoryReplayPlayerIdentityDto>,
)
