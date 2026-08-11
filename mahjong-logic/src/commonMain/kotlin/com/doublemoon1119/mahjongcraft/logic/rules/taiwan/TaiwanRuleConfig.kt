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
 * @property initialHandSize 初始手牌張數，預設為 16。
 * @property deadTileCount 王牌（死牌）張數，預設為 16。
 * @property minimumWinConstraint 起胡番數限制，預設為 0。
 * @property scoreConfig 積分配置，預設為 [TaiwanScoreConfig]。
 * @property gameLength 遊戲長度配置，預設使用 [TaiwanGameLength.OneGame]。
 * @property minPlayers 該規則要求的最小玩家人數
 * @property maxPlayers 該規則允許的最大玩家人數
 * @property multiRonPolicy 一炮多響時的結算方式，預設雙響、三響皆為頭跳。
 */
data class TaiwanRuleConfig(
    val useFlowerTiles: Boolean = true,
    override val minimumWinConstraint: Int = 0,
    override val scoreConfig: TaiwanScoreConfig = TaiwanScoreConfig(),
    override val gameLength: TaiwanGameLength = TaiwanGameLength.OneGame,
    override val multiRonPolicy: MultiRonPolicy = MultiRonPolicy(
        doubleRonResolution = RonResolution.NEAREST_WINNER,
        tripleRonResolution = RonResolution.NEAREST_WINNER,
    ),
) : MahjongRuleConfig {
    override val initialHandSize: Int = 16
    override val deadTileCount: Int = 16
    override val minPlayers: Int = 4
    override val maxPlayers: Int = 4

    init {
        validate()
    }
}
