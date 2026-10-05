package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.uuid.Uuid

/**
 * 規則中立的單一玩家分數與排行關鍵影格。
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

/**
 * 可供流局、胡牌及未來比賽結算重用的分數排行呈現資料。
 *
 * @property players 依固定座位順序保存的玩家關鍵影格。
 */
data class ScoreRankingPresentation(
    val players: List<ScoreRankingPlayer>,
) {
    init {
        require(players.isNotEmpty()) { "Score ranking presentation must contain at least one player" }
        require(players.map(ScoreRankingPlayer::playerId).distinct().size == players.size) {
            "Score ranking presentation player IDs must be unique"
        }
        require(players.map(ScoreRankingPlayer::previousRank).sorted() == (1..players.size).toList()) {
            "Previous ranks must form a complete one-based sequence"
        }
        require(players.map(ScoreRankingPlayer::currentRank).sorted() == (1..players.size).toList()) {
            "Current ranks must form a complete one-based sequence"
        }
    }
}
