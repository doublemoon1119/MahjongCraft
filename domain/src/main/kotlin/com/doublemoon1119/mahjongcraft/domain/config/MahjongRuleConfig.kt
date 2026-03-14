package com.doublemoon1119.mahjongcraft.domain.config

/**
 * 定義麻將遊戲最基礎的物理配置介面。
 *
 * 此介面僅包含所有麻將規則通用的物理參數，如手牌張數與牌組構成。
 */
interface MahjongRuleConfig {
    /** 初始手牌張數（不含摸牌）。例如日本麻將為 13，台灣麻將為 16。 */
    val initialHandSize: Int

    /** 牌山結束時需保留在場上不被使用的「王牌」張數。 */
    val deadTileCount: Int

    /** 該規則對應的積分配置。 */
    val scoreConfig: ScoreConfig

    /** 該規則對應的對局長度配置。 */
    val gameLength: GameLength

    /**
     * 最小胡牌番數或台數限制（翻縛）。
     * 日本麻將通常為 1，台灣麻將通常為 0。
     */
    val minimumWinConstraint: Int
}