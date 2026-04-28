package com.doublemoon1119.mahjongcraft.domain.fakes.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.fakes.config.FakeGameLength
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.RiichiScoreConfig

/**
 * 用於單元測試的日本麻將模擬規則配置。
 *
 * 實作 [RiichiRuleConfig] 介面，提供包含赤牌設定、立直規則、斷么等特性的預設值。
 *
 * @property redDoraCount 赤牌（五萬、五筒、五條）的總張數，預設為 3。
 * @property allowOpenTanyao 是否允許食斷（後付斷么），預設為 true。
 * @property useLocalYaku 是否啟用地方役種，預設為 false。
 * @property initialHandSize 初始手牌張數，預設為 13。
 * @property deadTileCount 王牌（死牌）張數，預設為 14。
 * @property minimumWinConstraint 起胡番數限制（通常為 1 番），預設為 1。
 * @property scoreConfig 日本麻將專屬的積分配置。
 * @property gameLength 遊戲長度配置，預設為 8 局（半莊戰）的模擬配置。
 */
class FakeRiichiRuleConfig(
    override val redDoraCount: Int = 3,
    override val allowOpenTanyao: Boolean = true,
    override val useLocalYaku: Boolean = false,
    override val initialHandSize: Int = 13,
    override val deadTileCount: Int = 14,
    override val minimumWinConstraint: Int = 1,
    override val scoreConfig: RiichiScoreConfig =  RiichiScoreConfig(
        initialScore = 25000,
        bustThreshold = 0,
        minPointsToWin = 30000
    ),
    override val gameLength: FakeGameLength = FakeGameLength(8, "Hanchan")
) : RiichiRuleConfig