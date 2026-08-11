package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.config.validate

/**
 * 日本麻將（Riichi Mahjong）特有的規則配置介面。
 *
 * 繼承自 [MahjongRuleConfig] 並增加與日麻計分和道具相關的參數。
 *
 * 本模組未直接對應到欄位的規則判定（例如包牌等結算規則），
 * 以 [M League 公式競技規則](https://m-league.jp/about/) 為準。
 *
 * @property redDoraCount 赤牌（五萬、五筒、五條）的總張數，預設為 3，常見配置為 3 張（五萬、五筒、五條各一）或 4 張 （五筒兩張，五萬、五條各一）。
 * @property allowOpenTanyao 是否允許食斷（斷么九鳴牌有效），預設為 true。
 * @property useLocalYaku 是否啟用古役（Local Yaku），預設為 false。
 * @property initialHandSize 初始手牌張數，預設為 13。
 * @property deadTileCount 王牌（死牌）張數，預設為 14。
 * @property minimumWinConstraint 起胡番數限制（通常為 1 番），預設為 1。
 * @property scoreConfig 日本麻將專屬的積分配置。
 * @property gameLength 遊戲長度配置，預設為 [RiichiGameLength.OneGame]。
 * @property minPlayers 該規則要求的最小玩家人數
 * @property maxPlayers 該規則允許的最大玩家人數
 * @property multiRonPolicy 一炮多響時的結算方式，預設雙響、三響皆為多家和。此欄位刻意不依循上述
 *   M League 基準，預設採用多家和以貼近多數玩家熟悉的體驗，可依需求另行設定。
 */
data class RiichiRuleConfig(
    val redDoraCount: Int = 3,
    val allowOpenTanyao: Boolean = true,
    val useLocalYaku: Boolean = false,
    override val minimumWinConstraint: Int = 1,
    override val scoreConfig: RiichiScoreConfig = RiichiScoreConfig(),
    override val gameLength: RiichiGameLength = RiichiGameLength.OneGame,
    override val multiRonPolicy: MultiRonPolicy = MultiRonPolicy(
        doubleRonResolution = RonResolution.ALL_WINNERS,
        tripleRonResolution = RonResolution.ALL_WINNERS,
    ),
) : MahjongRuleConfig {
    override val initialHandSize: Int = 13
    override val deadTileCount: Int = 14
    override val minPlayers: Int = 4
    override val maxPlayers: Int = 4

    init {
        validate()
    }
}
