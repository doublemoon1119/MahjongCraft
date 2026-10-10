package com.doublemoon1119.mahjongcraft.flow.common.game.history.query
import kotlin.uuid.Uuid

/**
 * 歷史對局的參與者摘要。
 *
 * @property seatIndex 座位索引。
 * @property playerId 玩家 UUID。
 * @property aiStrategyId AI 策略 ID。
 */
data class HistoryParticipantSummary(
    val seatIndex: Int,
    val playerId: Uuid,
    val aiStrategyId: String?,
)
