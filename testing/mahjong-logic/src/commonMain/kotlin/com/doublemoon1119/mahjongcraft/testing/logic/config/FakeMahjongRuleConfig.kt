package com.doublemoon1119.mahjongcraft.testing.logic.config

import com.doublemoon1119.mahjongcraft.logic.config.GameLength
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.config.ScoreConfig

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
 * @property multiRonPolicy 一炮多響時的結算方式，預設雙響、三響皆為頭跳。
 */
class FakeMahjongRuleConfig(
    override val initialHandSize: Int = 13,
    override val deadTileCount: Int = 14,
    override val minimumWinConstraint: Int = 1,
    override val scoreConfig: ScoreConfig = FakeScoreConfig(),
    override val gameLength: GameLength = FakeGameLength(),
    override val minPlayers: Int = 4,
    override val maxPlayers: Int = 4,
    override val multiRonPolicy: MultiRonPolicy = MultiRonPolicy(
        doubleRonResolution = RonResolution.NEAREST_WINNER,
        tripleRonResolution = RonResolution.NEAREST_WINNER,
    ),
) : MahjongRuleConfig
