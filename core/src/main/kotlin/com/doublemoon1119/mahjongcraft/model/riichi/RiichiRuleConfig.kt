package com.doublemoon1119.mahjongcraft.model.riichi

import com.doublemoon1119.mahjongcraft.model.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.model.ScoreConfig

/**
 * 日本麻將特有的積分配置實作。
 *
 * @property initialScore 初始點數，通常為 25000。
 * @property bustThreshold 擊飛門檻，通常為 0（點數小於 0 則結束）。
 */
data class RiichiScoreConfig(
    override val initialScore: Int = 25000,
    override val bustThreshold: Int? = 0
) : ScoreConfig

/**
 * 日本麻將（Riichi Mahjong）特有的規則配置介面。
 *
 * 繼承自 [MahjongRuleConfig] 並增加與日麻計分和道具相關的參數。
 */
interface RiichiRuleConfig : MahjongRuleConfig {
    /**
     * 遊戲中使用的赤寶牌（Aka Dora）總數。
     *
     * 常見配置為 3 張（五萬、五筒、五條各一）或 4 張。
     */
    val redDoraCount: Int

    /**
     * 覆寫計分配置為日麻專用格式。
     * */
    override val scoreConfig: RiichiScoreConfig
}