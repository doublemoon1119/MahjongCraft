package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.layout.TaiwanWallLayout
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.opening.TaiwanWallOpeningPolicy
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 針對 [TaiwanRuleModule] 進行的單元測試。
 *
 * 驗證該模組是否能針對台灣麻將規則正確生產對應的領域層組件。
 */
class TaiwanRuleModuleTest {

    private val module: MahjongRuleModule<TaiwanRuleConfig> = TaiwanRuleModule(
        id = "mahjongcraft:taiwan",
        config = TaiwanRuleConfig(),
    )

    /**
     * 驗證建立的牌山工廠是否為台灣麻將實作。
     */
    @Test
    fun `test create wall factory returns taiwan implementation`() {
        val factory = module.createWallFactory()
        assertTrue(factory is TaiwanWallFactory)
    }

    /** 驗證規則模組提供四人台灣麻將的三骰開門 policy。 */
    @Test
    fun `test create wall opening policy returns taiwan implementation`() {
        assertSame(TaiwanWallOpeningPolicy, module.createWallOpeningPolicy())
    }

    /** 驗證規則模組提供台灣麻將的牌牆布局。 */
    @Test
    fun `test create wall layout returns taiwan implementation`() {
        assertTrue(module.createWallLayout() is TaiwanWallLayout)
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

    /**
     * 驗證台灣麻將目前沒有摸牌後需要清除的規則特有狀態，直接回傳玩家本身。
     */
    @Test
    fun `test onPlayerDrew returns player as-is`() {
        val player = FakeMahjongPlayerFactory.create()

        assertSame(player, module.onPlayerDrew(player))
    }

    /**
     * 驗證台灣麻將目前沒有鳴牌後需要清除的規則特有狀態，直接回傳玩家列表本身。
     */
    @Test
    fun `test onMeldClaimed returns players as-is`() {
        val players = listOf(FakeMahjongPlayerFactory.create(), FakeMahjongPlayerFactory.create())

        assertSame(players, module.onMeldClaimed(players))
    }

    /**
     * 驗證台灣麻將目前沒有包牌這個機制，直接回傳玩家本身。
     */
    @Test
    fun `test applyPaoLiabilityIfTriggered returns player as-is`() {
        val player = FakeMahjongPlayerFactory.create()
        val calledTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)

        assertSame(player, module.applyPaoLiabilityIfTriggered(player, calledTile, RelativeDirection.Left))
    }

    /**
     * 驗證台灣麻將目前沒有自摸結算的實作，回傳 null。
     */
    @Test
    fun `test declareTsumo returns null`() {
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val player = FakeMahjongPlayerFactory.create(hand = Hand(lastDrawn = winningTile))
        val table = FakeTableStateFactory.create(players = listOf(player))

        assertNull(module.declareTsumo(table, player))
    }

    /**
     * 驗證台灣麻將目前沒有榮和結算的實作，回傳 null。
     */
    @Test
    fun `test declareRon returns null`() {
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val player = FakeMahjongPlayerFactory.create()
        val table = FakeTableStateFactory.create(players = listOf(player))

        assertNull(module.declareRon(table, player, winningTile, discarderId = Uuid.random()))
    }

    /**
     * 驗證台灣麻將目前沒有榮和結算的實作，即使 isRobbingKan = true（搶槓）也一樣固定回傳 null。
     */
    @Test
    fun `test declareRon returns null even when isRobbingKan is true`() {
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val player = FakeMahjongPlayerFactory.create()
        val table = FakeTableStateFactory.create(players = listOf(player))

        assertNull(module.declareRon(table, player, winningTile, discarderId = Uuid.random(), isRobbingKan = true))
    }

    /**
     * 驗證台灣麻將目前沒有立直/供託這個機制，回傳 null。
     */
    @Test
    fun `test collectStickPot returns null`() {
        val table = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create()))

        assertNull(module.collectStickPot(table))
    }

    /**
     * 驗證台灣麻將目前沒有流局結算的實作，回傳 null。
     */
    @Test
    fun `test declareExhaustiveDraw returns null`() {
        val table = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create()))

        assertNull(module.declareExhaustiveDraw(table))
    }

    /**
     * 驗證台灣麻將目前沒有多家和判定為流局這個機制的具體流局原因型別，回傳 null。
     */
    @Test
    fun `test resolveMultiRonAbortiveDraw returns null`() {
        assertNull(module.resolveMultiRonAbortiveDraw())
    }

    /**
     * 驗證台灣麻將目前沒有四風連打這個機制，回傳 null。
     */
    @Test
    fun `test resolveSuufonRenda returns null`() {
        val table = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create()))

        assertNull(module.resolveSuufonRenda(table))
    }

    /**
     * 驗證台灣麻將目前沒有立直、也就沒有四家立直這個機制，回傳 null。
     */
    @Test
    fun `test resolveSuuchaRiichi returns null`() {
        val table = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create()))

        assertNull(module.resolveSuuchaRiichi(table))
    }

    /**
     * 驗證台灣麻將目前沒有四槓散了這個機制，回傳 null。
     */
    @Test
    fun `test resolveSuukanNagare returns null`() {
        val table = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create()))

        assertNull(module.resolveSuukanNagare(table))
    }
}
