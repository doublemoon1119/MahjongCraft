package com.doublemoon1119.mahjongcraft.domain.riichi

import com.doublemoon1119.mahjongcraft.testing.fakes.FakeRiichiRuleConfig
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 針對 [RiichiRuleModule] 進行的單元測試。
 *
 * 驗證該模組是否能針對日本麻將規則正確生產對應的領域層組件。
 */
class RiichiRuleModuleTest {

    private val module = RiichiRuleModule()
    private val config = FakeRiichiRuleConfig()

    /**
     * 驗證建立的牌山工廠是否為日本麻將實作。
     */
    @Test
    fun `test create wall factory returns riichi implementation`() {
        val factory = module.createWallFactory(config)
        assertTrue(factory is RiichiWallFactory)
    }

    /**
     * 驗證建立的牌河是否為日本麻將實作。
     */
    @Test
    fun `test create discard pile returns riichi implementation`() {
        val discardPile = module.createDiscardPile(config)
        assertTrue(discardPile is RiichiDiscardPile)
    }
}