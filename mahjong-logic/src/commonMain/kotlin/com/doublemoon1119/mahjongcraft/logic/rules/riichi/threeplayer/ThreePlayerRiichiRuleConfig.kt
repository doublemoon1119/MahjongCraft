package com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer

import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.config.validate
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameLength
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig

/**
 * 三人日本麻將的規則配置。
 *
 * 牌組沒有二～八萬（108 張），不能吃，北可以拔出當寶牌；自摸時少一家支付（自摸損）。
 *
 * @property redDoraCount 赤寶牌總張數：0 或 2（五筒、五條各一）。
 * @property allowOpenTanyao 是否允許食斷，預設為 true。
 * @property useLocalYaku 是否啟用古役，預設為 false。
 * @property minimumWinConstraint 起胡番數限制，預設為 1。
 * @property scoreConfig 積分配置；預設起始 35000 點、一位必要點數 40000 點。
 * @property gameLength 遊戲長度，預設為 [RiichiGameLength.OneGame]。
 * @property multiRonPolicy 一炮多響時的結算方式；三人對局最多兩家同時榮和，預設兩家都和。
 */
data class ThreePlayerRiichiRuleConfig(
    override val redDoraCount: Int = THREE_PLAYER_RED_DORA_COUNT,
    override val allowOpenTanyao: Boolean = true,
    override val useLocalYaku: Boolean = false,
    override val minimumWinConstraint: Int = 1,
    override val scoreConfig: RiichiScoreConfig = RiichiScoreConfig(initialScore = 35000, minPointsToWin = 40000),
    override val gameLength: RiichiGameLength = RiichiGameLength.OneGame,
    override val multiRonPolicy: MultiRonPolicy = MultiRonPolicy(
        doubleRonResolution = RonResolution.ALL_WINNERS,
        tripleRonResolution = RonResolution.ALL_WINNERS,
    ),
) : RiichiFamilyRuleConfig {
    override val initialHandSize: Int = 13

    /**
     * 王牌固定 14 張：最前面 8 張嶺上牌，其後是寶牌與裏寶牌指示牌。每次補牌都把活牌尾端補進王牌末端，
     * 第 3、4 組槓寶牌指示牌就落在補進來的牌上。
     */
    override val deadTileCount: Int = 14
    override val minPlayers: Int = 3
    override val maxPlayers: Int = 3
    override val revealsClosedKanTiles: Boolean = true
    override val usesThreePlayerTiles: Boolean = true

    /** 每圈三局：一局戰 1 局、東風戰 3 局、半莊 6 局。 */
    override val scheduledRoundCount: Int
        get() = when (gameLength) {
            RiichiGameLength.OneGame -> 1
            RiichiGameLength.East -> ROUNDS_PER_WIND
            RiichiGameLength.TwoWinds -> ROUNDS_PER_WIND * 2
        }

    /** 槓與拔北各自最多 4 次，共用 8 張嶺上牌。 */
    override val rinshanTileCount: Int = 8

    init {
        require(redDoraCount == 0 || redDoraCount == THREE_PLAYER_RED_DORA_COUNT) {
            "Three-player riichi supports 0 or $THREE_PLAYER_RED_DORA_COUNT red fives, got $redDoraCount"
        }
        validate()
    }

    companion object {
        /** 五筒、五條各一張赤牌。 */
        const val THREE_PLAYER_RED_DORA_COUNT: Int = 2

        /** 每圈的局數。 */
        private const val ROUNDS_PER_WIND: Int = 3
    }
}
