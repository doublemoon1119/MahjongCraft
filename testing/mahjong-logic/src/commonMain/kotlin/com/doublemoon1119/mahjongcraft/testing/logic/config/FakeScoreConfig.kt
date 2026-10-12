package com.doublemoon1119.mahjongcraft.testing.logic.config

import com.doublemoon1119.mahjongcraft.logic.config.ScoreConfig

/**
 * 用於測試的模擬積分配置。
 *
 * @property initialScore 每位玩家的初始分數，預設為 25000。
 * @property bustThreshold 擊飛門檻，預設為 0；設為 null 表示不設門檻。
 */
class FakeScoreConfig(
    override val initialScore: Int = 25000,
    override val bustThreshold: Int? = 0,
) : ScoreConfig
