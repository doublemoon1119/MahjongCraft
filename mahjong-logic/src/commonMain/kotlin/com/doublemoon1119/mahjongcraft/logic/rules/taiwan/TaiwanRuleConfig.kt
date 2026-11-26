package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.config.validate

/**
 * 台灣麻將特有的規則配置介面。
 *
 * 繼承自 [MahjongRuleConfig] 並增加與台灣麻將補花機制相關的開關。
 *
 * @property useFlowerTiles 指定是否使用花牌。
 * @property fourWindsAbortiveDraw 是否在第一巡四位玩家依序打出相同風牌時流局。
 * @property kanClearsPassedWin 暗槓或加槓前後等待牌相同時，是否額外解除過水。
 * @property generalReadyDeclaration 是否可在天聽、地聽以外的時機宣告聽牌（報聽）並計報聽台，預設關閉。
 * @property paoLiability 是否採用大三元、大四喜的包牌，預設關閉。
 * @property initialHandSize 初始手牌張數，預設為 16。
 * @property reservedTileCount 牌山尾端由規則保留、不供一般摸牌使用的張數，固定為 16。
 * @property minimumWinConstraint 起胡台數限制，預設為 0。
 * @property scoreConfig 積分配置，預設為 [TaiwanScoreConfig]。
 * @property gameLength 遊戲長度配置，預設使用 [TaiwanGameLength.OneGame]。
 * @property minPlayers 該規則要求的最小玩家人數
 * @property maxPlayers 該規則允許的最大玩家人數
 * @property multiRonPolicy 一炮多響時的結算方式，預設雙響、三響皆由距離放銃者最近的玩家和牌。
 * @property revealsClosedKanTiles 暗槓身份是否公開，恆為 false——台灣麻將暗槓完全不公開，直到自摸
 *   或流局才揭露。
 */
data class TaiwanRuleConfig(
    val useFlowerTiles: Boolean = true,
    val fourWindsAbortiveDraw: Boolean = false,
    val kanClearsPassedWin: Boolean = false,
    val generalReadyDeclaration: Boolean = false,
    val paoLiability: Boolean = false,
    override val minimumWinConstraint: Int = 0,
    override val scoreConfig: TaiwanScoreConfig = TaiwanScoreConfig(),
    override val gameLength: TaiwanGameLength = TaiwanGameLength.OneGame,
    override val multiRonPolicy: MultiRonPolicy = MultiRonPolicy(
        doubleRonResolution = RonResolution.NEAREST_WINNER,
        tripleRonResolution = RonResolution.NEAREST_WINNER,
    ),
) : MahjongRuleConfig {
    override val initialHandSize: Int = 16
    override val reservedTileCount: Int = 16
    override val minPlayers: Int = 4
    override val maxPlayers: Int = 4
    override val revealsClosedKanTiles: Boolean = false

    init {
        validate()
    }
}
