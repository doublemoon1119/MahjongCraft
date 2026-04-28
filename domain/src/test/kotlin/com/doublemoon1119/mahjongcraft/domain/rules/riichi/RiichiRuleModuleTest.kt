package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.domain.fakes.FakeRiichiRuleConfig
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 針對 [RiichiRuleModule] 進行的單元測試。
 *
 * 驗證該模組是否能針對日本麻將規則正確生產對應的領域層組件。
 */
class RiichiRuleModuleTest {

    private val config = FakeRiichiRuleConfig()
    private val module: MahjongRuleModule<RiichiRuleConfig> = RiichiRuleModule("mahjongcraft:riichi", config)

    /**
     * 驗證建立的牌山工廠是否為日本麻將實作。
     */
    @Test
    fun `test create wall factory returns riichi implementation`() {
        val factory = module.createWallFactory()
        assertTrue(factory is RiichiWallFactory)
    }

    /**
     * 驗證建立的牌河是否為日本麻將實作。
     */
    @Test
    fun `test create discard pile returns riichi implementation`() {
        val discardPile = module.createDiscardPile()
        assertTrue(discardPile is RiichiDiscardPile)
    }

    /**
     * 驗證建立的向聽數計算器是否為日本麻將實作。
     */
    @Test
    fun `test create shanten calculator returns riichi implementation`() {
        val discardPile = module.createShantenCalculator()
        assertTrue(discardPile is RiichiShantenCalculator)
    }

    /**
     * 驗證建立的合法動作判定器是否為日本麻將實作。
     */
    @Test
    fun `test create legal action validator returns riichi implementation`() {
        val discardPile = module.createLegalActionValidator()
        assertTrue(discardPile is RiichiLegalActionValidator)
    }

    /**
     * 驗證建立的手牌價值計算機是否為日本麻將實作。
     */
    @Test
    fun `test create hand value calculator returns riichi implementation`() {
        val discardPile = module.createHandValueCalculator()
        assertTrue(discardPile is RiichiHandValueCalculator)
    }

    /**
     * 驗證建立的手牌價值上下文計算機是否為日本麻將實作。
     */
    @Test
    fun `test create hand value context calculator returns riichi implementation`() {
        val discardPile = module.createHandValueContextCalculator()
        assertTrue(discardPile is RiichiHandValueContextCalculator)
    }
}