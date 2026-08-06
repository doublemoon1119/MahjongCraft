package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.config.ScoreConfig

/**
 * 日本麻將特有的積分配置實作。
 *
 * @property initialScore 初始點數，通常為 25000。
 * @property bustThreshold 擊飛門檻，通常為 0（點數小於 0 則結束）。
 * @property minPointsToWin 一位必要點數（如 30000），未達標則觸發延長賽（西入）。
 */
data class RiichiScoreConfig(
    override val initialScore: Int = 25000,
    override val bustThreshold: Int? = 0,
    val minPointsToWin: Int = 30000,
) : ScoreConfig
