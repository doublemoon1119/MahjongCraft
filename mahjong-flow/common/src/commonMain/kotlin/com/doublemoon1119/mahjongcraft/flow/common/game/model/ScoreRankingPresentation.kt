package com.doublemoon1119.mahjongcraft.flow.common.game.model

import kotlin.math.roundToInt
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

/**
 * 單一動畫時間點的玩家排行列。
 *
 * @property player 原始權威關鍵影格。
 * @property score 當下顯示總分。
 * @property delta 當下顯示變化量。
 * @property position 從一開始算起的連續排行位置。
 */
data class AnimatedScoreRankingRow(
    val player: ScoreRankingPlayer,
    val score: Int,
    val delta: Int,
    val position: Double,
)

/** 流局與胡牌結算共用的純排行動畫計算。 */
object ScoreRankingAnimation {
    /** 將線性進度轉為 cubic ease-out，輸入與輸出皆限制於 0 至 1。 */
    fun easeOut(progress: Double): Double {
        val clamped = progress.coerceIn(0.0, 1.0)
        return 1.0 - (1.0 - clamped) * (1.0 - clamped) * (1.0 - clamped)
    }

    /** 依 [progress] 建立所有玩家當下的分數、變化量及連續排行位置。 */
    fun rows(presentation: ScoreRankingPresentation, progress: Double): List<AnimatedScoreRankingRow> {
        val eased = easeOut(progress)
        return presentation.players.map { player ->
            AnimatedScoreRankingRow(
                player = player,
                score = interpolate(player.previousScore, player.currentScore, eased),
                delta = interpolate(0, player.currentScore - player.previousScore, eased),
                position = interpolate(player.previousRank.toDouble(), player.currentRank.toDouble(), eased),
            )
        }
    }

    /** 依連續排行位置取得畫面當下的一至多人名次，讓跨越多名時依序顯示中間名次。 */
    fun liveRanks(rows: List<AnimatedScoreRankingRow>): Map<Uuid, Int> = rows
        .sortedWith(compareBy<AnimatedScoreRankingRow> { it.position }.thenBy { it.player.previousRank })
        .mapIndexed { index, row -> row.player.playerId to index + 1 }
        .toMap()

    /** 以整數終點保證最後一幀精確收斂的線性插值。 */
    private fun interpolate(start: Int, end: Int, progress: Double): Int = if (progress >= 1.0) {
        end
    } else {
        (start + (end - start) * progress).roundToInt()
    }

    /** 以雙精度保存排行列平滑位移的線性插值。 */
    private fun interpolate(start: Double, end: Double, progress: Double): Double = start + (end - start) * progress
}
