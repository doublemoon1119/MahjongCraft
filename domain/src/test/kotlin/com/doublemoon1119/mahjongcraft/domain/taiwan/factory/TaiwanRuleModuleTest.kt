package com.doublemoon1119.mahjongcraft.domain.taiwan.factory

import com.doublemoon1119.mahjongcraft.domain.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.testing.fakes.FakeTaiwanRuleConfig
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 針對 [TaiwanRuleModule] 進行的單元測試。
 *
 * 驗證該模組是否能針對台灣麻將規則正確生產對應的領域層組件。
 */
class TaiwanRuleModuleTest {

    private val module = TaiwanRuleModule()
    private val config = FakeTaiwanRuleConfig()

    /**
     * 驗證建立的牌山工廠是否為台灣麻將實作。
     */
    @Test
    fun `test create wall factory returns taiwan implementation`() {
        val factory = module.createWallFactory(config)
        assertTrue(factory is TaiwanWallFactory)
    }

    /**
     * 驗證建立的牌河是否為台灣麻將實作。
     */
    @Test
    fun `test create discard pile returns taiwan implementation`() {
        val discardPile = module.createDiscardPile(config)
        assertTrue(discardPile is TaiwanDiscardPile)
    }
}