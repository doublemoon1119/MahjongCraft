package com.doublemoon1119.mahjongcraft.testing.logic.config

import com.doublemoon1119.mahjongcraft.logic.config.ScoreConfig

/**
 * 用於測試的模擬積分配置。
 */
class FakeScoreConfig(
    override val initialScore: Int = 25000,
    override val bustThreshold: Int? = 0,
) : ScoreConfig
