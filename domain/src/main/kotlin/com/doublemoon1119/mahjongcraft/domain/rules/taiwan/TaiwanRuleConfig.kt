package com.doublemoon1119.mahjongcraft.domain.rules.taiwan

import com.doublemoon1119.mahjongcraft.domain.config.MahjongRuleConfig


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
 * @property isSpectateAllowed 允許在遊戲外的玩家能否看到遊戲內玩家的手牌，在牌河或者副露的牌則不在此限，預設為 true。
 * @property minPlayers 該規則要求的最小玩家人數
 * @property maxPlayers 該規則允許的最大玩家人數
 */
data class TaiwanRuleConfig(
    val useFlowerTiles: Boolean = true,
    override val minimumWinConstraint: Int = 0,
    override val scoreConfig: TaiwanScoreConfig = TaiwanScoreConfig(),
    override val gameLength: TaiwanGameLength = TaiwanGameLength.OneGame,
    override val isSpectateAllowed: Boolean = true
) : MahjongRuleConfig {
    override val initialHandSize: Int = 16
    override val deadTileCount: Int = 16
    override val minPlayers: Int = 4
    override val maxPlayers: Int = 4
}