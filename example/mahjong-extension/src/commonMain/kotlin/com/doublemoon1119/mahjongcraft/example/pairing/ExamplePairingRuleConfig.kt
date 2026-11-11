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
 * @property wallTileValues 會加入牌山的一萬至八萬點數；每種點數固定加入四張，至少選兩種。
 * @property publicFacesEnabled 是否讓每種點數四張中的三張帶公開牌面標記，所有人都看得到牌面。
 */
data class ExamplePairingRuleConfig(
    val exchangeEnabled: Boolean = false,
    val paymentPerPlayer: Int = 10,
    val roundCount: Int = 4,
    val wallTileValues: Set<Int> = (1..8).toSet(),
    val publicFacesEnabled: Boolean = false,
) : MahjongRuleConfig {
    override val scoreConfig: ExamplePairingScoreConfig = ExamplePairingScoreConfig()
    override val gameLength: ExamplePairingGameLength = ExamplePairingGameLength(roundCount)
    override val initialHandSize: Int = 3
    override val reservedTileCount: Int = 0
    override val minimumWinConstraint: Int = 0
    override val minPlayers: Int = 2
    override val maxPlayers: Int = minOf(4, (wallTileValues.size * 4 - 1) / 3)
    override val multiRonPolicy: MultiRonPolicy = MultiRonPolicy(
        doubleRonResolution = RonResolution.NEAREST_WINNER,
        tripleRonResolution = RonResolution.NEAREST_WINNER,
    )
    override val revealsClosedKanTiles: Boolean = false

    init {
        require(paymentPerPlayer in 1..100) { "paymentPerPlayer must be between 1 and 100" }
        require(roundCount in 1..16) { "roundCount must be between 1 and 16" }
        require(wallTileValues.size >= 2) { "wallTileValues must contain at least two values" }
        require(wallTileValues.all { it in 1..8 }) { "wallTileValues must be between 1 and 8" }
        require(maxPlayers >= minPlayers) { "wallTileValues do not provide enough tiles for two players" }
        validate()
    }
}
