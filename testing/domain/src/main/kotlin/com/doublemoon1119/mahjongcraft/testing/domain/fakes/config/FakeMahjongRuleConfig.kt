package com.doublemoon1119.mahjongcraft.testing.domain.fakes.config

import com.doublemoon1119.mahjongcraft.domain.config.GameLength
import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.domain.config.ScoreConfig

/**
 * 用於測試的模擬規則配置實作。
 *
 * 透過建構子參數提供預設值，方便在不同測試場景中快速調整核心參數。
 *
 * @property initialHandSize 初始手牌張數。
 * @property deadTileCount 王牌/保留牌張數。
 * @property minimumWinConstraint 起胡限制。
 * @property scoreConfig 積分配置，預設使用 [FakeScoreConfig]。
 * @property gameLength 對局長度配置，預設使用 [FakeGameLength]。
 */
class FakeMahjongRuleConfig(
    override val initialHandSize: Int = 13,
    override val deadTileCount: Int = 14,
    override val minimumWinConstraint: Int = 1,
    override val scoreConfig: ScoreConfig = FakeScoreConfig(),
    override val gameLength: GameLength = FakeGameLength(),
    override val isSpectateAllowed: Boolean = true,
    override val minPlayers: Int = 4,
    override val maxPlayers: Int = 4
) : MahjongRuleConfig