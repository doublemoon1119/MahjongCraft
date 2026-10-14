package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 歷史清單中可安全公開的參與者資訊。
 *
 *
 * @property seatIndex 對局中的座位索引。
 * @property playerId 玩家 UUID 字串；AI 座位也使用穩定識別碼。
 * @property aiStrategyId AI 策略識別碼；真人座位為 null。
 */
@Serializable
data class HistoryParticipantSummaryDto(
    val seatIndex: Int,
    val playerId: String,
    val aiStrategyId: String?,
)
