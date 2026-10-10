package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 單局桌況的 S2C 回覆。
 *
 * @property requestId 對應請求的識別碼。
 * @property matchId 回應所屬對局識別碼。
 * @property roundNumber 回應所屬局序號。
 * @property position 回應所屬局內位置。
 * @property state 完整桌況；不可提供時為 null。
 * @property errorCode 安全的查詢錯誤代碼。
 */
@Serializable
data class HistoryRoundStateResponseDto(
    val requestId: String,
    val matchId: String,
    val roundNumber: Int,
    val position: HistoryRoundPositionDto,
    val state: HistoryRoundStateDto?,
    val errorCode: HistoryQueryErrorCodeDto?,
)
