package com.doublemoon1119.mahjongcraft.domain.rules.taiwan

import com.doublemoon1119.mahjongcraft.domain.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.domain.fakes.FakeTaiwanRuleConfig
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 針對 [TaiwanRuleModule] 進行的單元測試。
 *
 * 驗證該模組是否能針對台灣麻將規則正確生產對應的領域層組件。
 */
class TaiwanRuleModuleTest {

    private val config = FakeTaiwanRuleConfig()
    private val module: MahjongRuleModule<TaiwanRuleConfig> = TaiwanRuleModule("mahjongcraft:taiwan", config)

    /**
     * 驗證建立的牌山工廠是否為台灣麻將實作。
     */
    @Test
    fun `test create wall factory returns taiwan implementation`() {
        val factory = module.createWallFactory()
        assertTrue(factory is TaiwanWallFactory)
    }

    /**
     * 驗證建立的牌河是否為台灣麻將實作。
     */
    @Test
    fun `test create discard pile returns taiwan implementation`() {
        val discardPile = module.createDiscardPile()
        assertTrue(discardPile is TaiwanDiscardPile)
    }

    /**
     * 驗證建立的向聽數計算器是否為台灣麻將實作。
     */
    @Test
    fun `test create shanten calculator returns taiwan implementation`() {
        val discardPile = module.createShantenCalculator()
        assertTrue(discardPile is TaiwanShantenCalculator)
    }

    /**
     * 驗證建立的合法動作判定器是否為台灣麻將實作。
     */
    @Test
    fun `test create legal action validator returns taiwan implementation`() {
        val discardPile = module.createLegalActionValidator()
        assertTrue(discardPile is TaiwanLegalActionValidator)
    }

    /**
     * 驗證建立的手牌價值計算機是否為台灣麻將實作。
     */
    @Ignore("TaiwanRuleModule.createHandValueCalculator is not yet implemented")
    @Test
    fun `test create hand value calculator returns taiwan implementation`() {
        // val discardPile = module.createHandValueCalculator()
        // TODO: assertTrue(discardPile is TaiwanHandValueCalculator)
    }

    /**
     * 驗證建立的手牌價值上下文計算機是否為台灣麻將實作。
     */
    @Ignore("TaiwanRuleModule.createHandValueContextCalculator is not yet implemented")
    @Test
    fun `test create hand value context calculator returns taiwan implementation`() {
        // val discardPile = module.createHandValueContextCalculator()
        // TODO: assertTrue(discardPile is TaiwanHandValueContextCalculator)
    }
}