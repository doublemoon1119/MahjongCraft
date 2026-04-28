package com.doublemoon1119.mahjongcraft.domain.fakes.rules.taiwan

import com.doublemoon1119.mahjongcraft.domain.fakes.config.FakeGameLength
import com.doublemoon1119.mahjongcraft.domain.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.domain.rules.taiwan.TaiwanScoreConfig

/**
 * 用於單元測試的台灣麻將模擬規則配置。
 *
 * 實作 [TaiwanRuleConfig] 介面，並提供符合台灣麻將常規的預設值（如 16 張手牌、8 張花牌）。
 * 透過建構子參數允許測試案例針對特定屬性進行覆蓋。
 *
 * @property useFlowerTiles 是否使用花牌，預設為 true。
 * @property initialHandSize 初始手牌張數，預設為 16。
 * @property deadTileCount 牌山尾端保留的死牌張數，預設為 8。
 * @property minimumWinConstraint 起胡台數限制，預設為 0。
 * @property scoreConfig 台灣麻將專屬的積分配置。
 * @property gameLength 遊戲長度配置，預設為 16 局的模擬配置。
 */
class FakeTaiwanRuleConfig(
    override val useFlowerTiles: Boolean = true,
    override val initialHandSize: Int = 16,
    override val deadTileCount: Int = 8,
    override val minimumWinConstraint: Int = 0,
    override val scoreConfig: TaiwanScoreConfig = TaiwanScoreConfig(
        baseScore = 30,
        pointPerTai = 10,
        initialScore = 0,
        bustThreshold = null
    ),
    override val gameLength: FakeGameLength = FakeGameLength(16, "TaiwanRound")
) : TaiwanRuleConfig