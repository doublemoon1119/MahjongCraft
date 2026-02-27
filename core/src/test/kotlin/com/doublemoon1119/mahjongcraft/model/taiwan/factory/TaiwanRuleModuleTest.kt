package com.doublemoon1119.mahjongcraft.model.taiwan.factory

import com.doublemoon1119.mahjongcraft.model.base.Tile
import com.doublemoon1119.mahjongcraft.model.config.GameLength
import com.doublemoon1119.mahjongcraft.model.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.model.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.model.taiwan.TaiwanScoreConfig
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 符合 TaiwanRuleConfig 介面的測試實作。
 */
class FakeTaiwanConfig(
    override val initialHandSize: Int = 16,
    override val tileSet: List<Tile> = emptyList(),
    override val deadTileCount: Int = 8,
    override val useFlowerTiles: Boolean = true,
    override val minimumWinConstraint: Int = 0,
    override val scoreConfig: TaiwanScoreConfig = TaiwanScoreConfig(
        baseScore = 30,
        pointPerTai = 10,
        initialScore = 0
    ),
    override val gameLength: GameLength = object : GameLength {
        override val totalRounds: Int = 16
        override val name: String = "OneRound"
    }
) : TaiwanRuleConfig

/**
 * 驗證 TaiwanRuleModule 是否正確生產台灣麻將專屬組件。
 */
class TaiwanRuleModuleTest {

    private val module = TaiwanRuleModule()
    private val config = FakeTaiwanConfig()

    /**
     * 驗證建立的牌山工廠是否為台灣麻將實作。
     */
    @Test
    fun `test create wall factory returns taiwan implementation`() {
        val factory = module.createWallFactory(config)
        assertTrue(factory is TaiwanWallFactory, "Factory should be an instance of TaiwanWallFactory.")
    }

    /**
     * 驗證建立的牌河是否為台灣麻將專用的 TaiwanDiscardPile。
     */
    @Test
    fun `test create discard pile returns taiwan implementation`() {
        val discardPile = module.createDiscardPile(config)
        assertTrue(discardPile is TaiwanDiscardPile, "DiscardPile should be an instance of TaiwanDiscardPile.")
    }
}