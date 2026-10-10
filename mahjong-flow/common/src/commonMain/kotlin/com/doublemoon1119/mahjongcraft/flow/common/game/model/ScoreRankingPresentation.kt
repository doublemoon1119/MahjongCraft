package com.doublemoon1119.mahjongcraft.flow.common.game.model

/**
 * 可供流局、胡牌及未來比賽結算重用的分數排行呈現資料。
 *
 * @property players 依固定座位順序保存的玩家資料。
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
