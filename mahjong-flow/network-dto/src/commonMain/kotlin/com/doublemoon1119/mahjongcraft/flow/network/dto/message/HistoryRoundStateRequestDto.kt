package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 單局桌況的 C2S 請求。
 *
 * @property requestId 配對非同步回覆的請求識別碼。
 * @property matchId 對局識別碼。
 * @property scope 查詢範圍。
 * @property roundNumber 局序號。
 * @property position 局內位置；開局或指定交易後。
 */
@Serializable
data class HistoryRoundStateRequestDto(
    val requestId: String,
    val matchId: String,
    val scope: HistoryQueryScopeDto,
    val roundNumber: Int,
    val position: HistoryRoundPositionDto,
)
