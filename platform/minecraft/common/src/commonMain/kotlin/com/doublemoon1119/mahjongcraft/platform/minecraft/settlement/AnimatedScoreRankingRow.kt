package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer

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
