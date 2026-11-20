package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 單局桌況的 S2C 回覆。
 *
 * @property requestId 對應請求的識別碼。
 * @property matchId 回應所屬對局識別碼。
 * @property roundNumber 回應所屬局序號。
 * @property position 要求的第一個局內位置。
 * @property states 從第一個位置起依先後排列的完整桌況；回覆大小或本局交易不足時少於要求的數量，失敗時為空。
 * @property errorCode 安全的查詢錯誤代碼。
 */
@Serializable
data class HistoryRoundStateResponseDto(
    val requestId: String,
    val matchId: String,
    val roundNumber: Int,
    val position: HistoryRoundPositionDto,
    val states: List<HistoryRoundStateDto>,
    val errorCode: HistoryQueryErrorCodeDto?,
)
