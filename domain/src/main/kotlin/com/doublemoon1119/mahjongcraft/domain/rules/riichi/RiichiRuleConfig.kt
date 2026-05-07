package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig


/**
 * 日本麻將（Riichi Mahjong）特有的規則配置介面。
 *
 * 繼承自 [MahjongRuleConfig] 並增加與日麻計分和道具相關的參數。
 *
 * @property redDoraCount 赤牌（五萬、五筒、五條）的總張數，預設為 3，常見配置為 3 張（五萬、五筒、五條各一）或 4 張 （五筒兩張，五萬、五條各一）。
 * @property allowOpenTanyao 是否允許食斷（斷么九鳴牌有效），預設為 true。
 * @property useLocalYaku 是否啟用古役（Local Yaku），預設為 false。
 * @property initialHandSize 初始手牌張數，預設為 13。
 * @property deadTileCount 王牌（死牌）張數，預設為 14。
 * @property minimumWinConstraint 起胡番數限制（通常為 1 番），預設為 1。
 * @property scoreConfig 日本麻將專屬的積分配置。
 * @property gameLength 遊戲長度配置，預設為 [RiichiGameLength.OneGame]。
 * @property isSpectateAllowed 允許在遊戲外的玩家能否看到遊戲內玩家的手牌，在牌河或者副露的牌則不在此限，預設為 true。
 * @property minPlayers 該規則要求的最小玩家人數
 * @property maxPlayers 該規則允許的最大玩家人數
 */
data class RiichiRuleConfig(
    val redDoraCount: Int = 3,
    val allowOpenTanyao: Boolean = true,
    val useLocalYaku: Boolean = false,
    override val minimumWinConstraint: Int = 1,
    override val scoreConfig: RiichiScoreConfig = RiichiScoreConfig(),
    override val gameLength: RiichiGameLength = RiichiGameLength.OneGame,
    override val isSpectateAllowed: Boolean = true,
) : MahjongRuleConfig {
    override val initialHandSize: Int = 13
    override val deadTileCount: Int = 14
    override val minPlayers: Int = 4
    override val maxPlayers: Int = 4
}
