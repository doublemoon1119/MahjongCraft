package com.doublemoon1119.mahjongcraft.model.riichi.factory

import com.doublemoon1119.mahjongcraft.model.base.Tile
import com.doublemoon1119.mahjongcraft.model.config.GameLength
import com.doublemoon1119.mahjongcraft.model.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.model.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.model.riichi.RiichiScoreConfig
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 符合 RiichiRuleConfig 介面的測試實作。
 */
class FakeRiichiConfig(
    override val initialHandSize: Int = 13,
    override val tileSet: List<Tile> = emptyList(),
    override val deadTileCount: Int = 14,
    override val redDoraCount: Int = 3,
    override val allowOpenTanyao: Boolean = true,
    override val useLocalYaku: Boolean = false,
    override val minimumWinConstraint: Int = 1,
    override val scoreConfig: RiichiScoreConfig = RiichiScoreConfig(
        initialScore = 25000,
        bustThreshold = 0,
        minPointsToWin = 30000
    ),
    override val gameLength: GameLength = object : GameLength {
        override val totalRounds: Int = 8
        override val name: String = "Hanchan"
    }
) : RiichiRuleConfig

/**
 * 驗證 RiichiRuleModule 是否正確生產日本麻將專屬組件。
 */
class RiichiRuleModuleTest {

    private val module = RiichiRuleModule()
    private val config = FakeRiichiConfig()

    /**
     * 驗證建立的牌山工廠是否為日本麻將實作。
     */
    @Test
    fun `test create wall factory returns riichi implementation`() {
        val factory = module.createWallFactory(config)
        assertTrue(factory is RiichiWallFactory, "Factory should be an instance of RiichiWallFactory.")
    }

    /**
     * 驗證建立的牌河是否為日本麻將專用的 RiichiDiscardPile。
     */
    @Test
    fun `test create discard pile returns riichi implementation`() {
        val discardPile = module.createDiscardPile(config)
        assertTrue(discardPile is RiichiDiscardPile, "DiscardPile should be an instance of RiichiDiscardPile.")
    }
}