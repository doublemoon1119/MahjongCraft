package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 針對 [TaiwanRuleModule] 進行的單元測試。
 *
 * 驗證該模組是否能針對台灣麻將規則正確生產對應的領域層組件。
 */
class TaiwanRuleModuleTest {

    private val module: MahjongRuleModule<TaiwanRuleConfig> = TaiwanRuleModule(
        id = "mahjongcraft:taiwan",
        config = TaiwanRuleConfig()
    )

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

    /**
     * 驗證台灣麻將目前沒有動態桌況狀態的需求，回傳 null。
     */
    @Test
    fun `test create initial dynamic state returns null`() {
        assertNull(module.createInitialDynamicState())
    }

    /**
     * 驗證台灣麻將目前沒有玩家規則狀態的需求，回傳 null。
     */
    @Test
    fun `test create initial player rule state returns null`() {
        assertNull(module.createInitialPlayerRuleState())
    }

    /**
     * 驗證台灣麻將目前沒有立直宣告這個機制，回傳 null。
     */
    @Test
    fun `test declareRiichi returns null`() {
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val hand = Hand(lastDrawn = discardedTile)
        val discardResult = hand.discardById(discardedTile.id)!!
        val player = FakeMahjongPlayerFactory.create(hand = hand)
        val table = FakeTableStateFactory.create(players = listOf(player))

        assertNull(module.declareRiichi(table, player, discardResult))
    }
}