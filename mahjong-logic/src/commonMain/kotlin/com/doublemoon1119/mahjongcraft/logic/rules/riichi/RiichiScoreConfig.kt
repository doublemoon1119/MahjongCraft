package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.config.ScoreConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule

/**
 * 日本麻將特有的積分配置實作。
 *
 * @property initialScore 初始點數，通常為 25000。
 * @property bustThreshold 擊飛門檻，通常為 0（點數小於 0 則結束）。
 * @property minPointsToWin 一位必要點數（如 30000），未達標時東風戰觸發南入、半莊觸發西入。
 * @property notenPenaltyUnit 一般流局（牌山摸盡）時，不聽罰符的基數，標準值為 1000。實際罰符總額
 *   由 [MahjongRuleModule.declareExhaustiveDraw] 依對局人數換算為「基數 * (人數 - 1)」（四人對局為 3000、三人對局為 2000），
 *   而非這裡直接設定固定總額——這樣不同玩家人數的對局才能共用同一個基數設定，不需要為每種人數各自設定總額。
 */
data class RiichiScoreConfig(
    override val initialScore: Int = 25000,
    override val bustThreshold: Int? = 0,
    val minPointsToWin: Int = 30000,
    val notenPenaltyUnit: Int = 1000,
) : ScoreConfig
