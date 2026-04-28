package com.doublemoon1119.mahjongcraft.domain.rules.taiwan

import com.doublemoon1119.mahjongcraft.domain.config.ScoreConfig

/**
 * 台灣麻將特有的積分配置實作。
 * @property baseScore 底分（如：30, 100 等）。
 * @property pointPerTai 台分（每一台對應的分數，如：10, 20 等）。
 * @property initialScore 初始分數，通常台麻在純計點模式下可設為 0。
 * @property bustThreshold 台灣麻將通常沒有擊飛機制，故設為 null。
 */
data class TaiwanScoreConfig(
    val baseScore: Int = 30,
    val pointPerTai: Int = 10,
    override val initialScore: Int = 0,
    override val bustThreshold: Int? = null
) : ScoreConfig