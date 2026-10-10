package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/**
 * 單一玩家的權威終局排行快照。
 *
 * @property playerId 玩家 Uuid。
 * @property seatIndex 對局開始時的固定座位 index。
 * @property isAi 是否由 AI 操控。
 * @property initialSeatIndex 整場固定起家順位，供規則同分順位與呈現 fallback 使用。
 * @property finalScore 收取剩餘供託後的最終桌上點數。
 * @property finalRank 規則已判定的最終名次，從 1 開始。
 */
data class MatchSettlementPlayerPresentation(
    val playerId: Uuid,
    val seatIndex: Int,
    val isAi: Boolean,
    val initialSeatIndex: Int,
    val finalScore: Int,
    val finalRank: Int,
)
