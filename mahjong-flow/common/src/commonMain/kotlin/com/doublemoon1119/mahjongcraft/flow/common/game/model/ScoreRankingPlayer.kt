package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/**
 * 規則中立的單一玩家結算前後分數與名次。
 *
 * @property playerId 玩家 Uuid。
 * @property seatIndex 固定座位 index。
 * @property isAi 是否由 AI 操控。
 * @property previousScore 結算前總分。
 * @property currentScore 結算後總分。
 * @property previousRank 結算前名次，從 1 開始。
 * @property currentRank 結算後名次，從 1 開始。
 */
data class ScoreRankingPlayer(
    val playerId: Uuid,
    val seatIndex: Int,
    val isAi: Boolean,
    val previousScore: Int,
    val currentScore: Int,
    val previousRank: Int,
    val currentRank: Int,
)
