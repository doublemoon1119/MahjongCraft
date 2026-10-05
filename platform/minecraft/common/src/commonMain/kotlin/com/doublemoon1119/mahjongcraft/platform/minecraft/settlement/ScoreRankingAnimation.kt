package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

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

    /** 依連續排行位置取得呈現當下的一至多人名次，讓跨越多名時依序顯示中間名次。 */
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
