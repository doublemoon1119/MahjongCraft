package com.doublemoon1119.mahjongcraft.model.taiwan

import com.doublemoon1119.mahjongcraft.model.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.model.ScoreConfig

/**
 * 台灣麻將特有的積分配置實作。
 * @property baseScore 底分（如：30, 100 等）。
 * @property pointPerTai 台分（每一台對應的分數，如：10, 20 等）。
 * @property initialScore 初始分數，通常台麻在純計點模式下可設為 0。
 * @property bustThreshold 台灣麻將通常沒有擊飛機制，故設為 null。
 */
data class TaiwanScoreConfig(
    val baseScore: Int,
    val pointPerTai: Int,
    override val initialScore: Int = 0,
    override val bustThreshold: Int? = null
) : ScoreConfig

/**
 * 台灣麻將特有的規則配置介面。
 *
 * 繼承自 [MahjongRuleConfig] 並增加與台灣麻將補花機制相關的開關。
 */
interface TaiwanRuleConfig : MahjongRuleConfig {
    /**
     * 是否在遊戲中使用花牌（春夏秋冬、梅蘭竹菊）。
     *
     * 此布林值決定了遊戲流程中是否包含補花動作以及相關的胡牌類型。
     */
    val useFlowerTiles: Boolean

    /**
     * 覆寫計分配置為台麻專用格式，包含底與台的計算參數。
     * */
    override val scoreConfig: TaiwanScoreConfig
}