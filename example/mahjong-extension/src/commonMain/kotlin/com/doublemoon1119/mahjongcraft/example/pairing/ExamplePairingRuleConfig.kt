package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.config.validate

/**
 * 簡易配對麻將的規則設定。
 *
 * @property exchangeEnabled 是否在每局開始前啟用兩張換牌。
 * @property paymentPerPlayer 自摸時每位其他玩家支付的點數，範圍為 1～100。
 * @property roundCount 整場對局的局數，範圍為 1～16。
 */
data class ExamplePairingRuleConfig(
    val exchangeEnabled: Boolean = false,
    val paymentPerPlayer: Int = 10,
    val roundCount: Int = 4,
) : MahjongRuleConfig {
    override val scoreConfig: ExamplePairingScoreConfig = ExamplePairingScoreConfig()
    override val gameLength: ExamplePairingGameLength = ExamplePairingGameLength(roundCount)
    override val initialHandSize: Int = 3
    override val deadTileCount: Int = 0
    override val minimumWinConstraint: Int = 0
    override val minPlayers: Int = 2
    override val maxPlayers: Int = 4
    override val multiRonPolicy: MultiRonPolicy = MultiRonPolicy(
        doubleRonResolution = RonResolution.NEAREST_WINNER,
        tripleRonResolution = RonResolution.NEAREST_WINNER,
    )
    override val revealsClosedKanTiles: Boolean = false

    init {
        require(paymentPerPlayer in 1..100) { "paymentPerPlayer must be between 1 and 100" }
        require(roundCount in 1..16) { "roundCount must be between 1 and 16" }
        validate()
    }
}
