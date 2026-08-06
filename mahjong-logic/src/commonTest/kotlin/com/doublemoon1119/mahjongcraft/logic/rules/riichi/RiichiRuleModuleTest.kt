package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 針對 [RiichiRuleModule] 進行的單元測試。
 *
 * 驗證該模組是否能針對日本麻將規則正確生產對應的領域層組件。
 */
class RiichiRuleModuleTest {

    private val module: MahjongRuleModule<RiichiRuleConfig> = RiichiRuleModule(
        id = "mahjongcraft:riichi",
        config = RiichiRuleConfig()
    )

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

    /**
     * 驗證建立的初始動態桌況狀態是否為日本麻將實作。
     */
    @Test
    fun `test create initial dynamic state returns riichi implementation`() {
        val dynamicState = module.createInitialDynamicState()
        assertTrue(dynamicState is RiichiDynamicState)
    }

    /**
     * 驗證建立的初始玩家規則狀態是否為日本麻將實作。
     */
    @Test
    fun `test create initial player rule state returns riichi implementation`() {
        val playerRuleState = module.createInitialPlayerRuleState()
        assertTrue(playerRuleState is RiichiPlayerState)
    }

    /**
     * 驗證日麻規則狀態齊全時，宣告立直能正確套用捨牌紀錄、玩家立直狀態與立直棒數量。
     */
    @Test
    fun `test declareRiichi applies riichi state when rule states are riichi`() {
        val handTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val hand = Hand(tiles = listOf(handTile), lastDrawn = discardedTile)
        val discardResult = hand.discardById(discardedTile.id)!!

        // 已經打過一輪牌，確保不是雙立直（雙立直的情境另外由 DeclareRiichiUseCaseTest 驗證）
        val priorDiscardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South))
        val player = FakeMahjongPlayerFactory.create(
            hand = hand,
            discardPile = priorDiscardPile,
            playerRuleState = RiichiPlayerState()
        ).copy(score = 25000)
        val table = FakeTableStateFactory.create(
            players = listOf(player),
            config = module.config,
            dynamicRuleState = RiichiDynamicState()
        )

        val result = module.declareRiichi(table, player, discardResult)

        requireNotNull(result)
        val riichiState = result.player.playerRuleState as RiichiPlayerState
        assertEquals(discardedTile, riichiState.riichiTile)
        assertEquals(null, riichiState.doubleRiichiTile)
        assertTrue(riichiState.isIppatsu)
        assertEquals(24000, result.player.score)
        assertEquals(2, result.player.discardPile.entries.size)
        assertTrue((result.player.discardPile.entries.last() as RiichiDiscardEntry).isRiichi)
        assertEquals(1, (result.dynamicRuleState as RiichiDynamicState).riichiStickCount)
    }

    /**
     * 驗證玩家的規則狀態不是 [RiichiPlayerState] 時，宣告立直回傳 null（防呆）。
     */
    @Test
    fun `test declareRiichi returns null when player rule state is not riichi`() {
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val hand = Hand(lastDrawn = discardedTile)
        val discardResult = hand.discardById(discardedTile.id)!!

        val player = FakeMahjongPlayerFactory.create(hand = hand, playerRuleState = null)
        val table = FakeTableStateFactory.create(players = listOf(player), dynamicRuleState = RiichiDynamicState())

        assertNull(module.declareRiichi(table, player, discardResult))
    }

    /**
     * 驗證玩家的牌河不是 [RiichiDiscardPile] 時，宣告立直回傳 null（防呆）。
     */
    @Test
    fun `test declareRiichi returns null when discard pile is not riichi`() {
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val hand = Hand(lastDrawn = discardedTile)
        val discardResult = hand.discardById(discardedTile.id)!!

        val player = FakeMahjongPlayerFactory.create(
            hand = hand,
            discardPile = FakeDiscardPile(),
            playerRuleState = RiichiPlayerState()
        )
        val table = FakeTableStateFactory.create(players = listOf(player), dynamicRuleState = RiichiDynamicState())

        assertNull(module.declareRiichi(table, player, discardResult))
    }

    /**
     * 驗證桌況的動態規則狀態不是 [RiichiDynamicState] 時，宣告立直回傳 null（防呆）。
     */
    @Test
    fun `test declareRiichi returns null when dynamic rule state is not riichi`() {
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val hand = Hand(lastDrawn = discardedTile)
        val discardResult = hand.discardById(discardedTile.id)!!

        val player = FakeMahjongPlayerFactory.create(
            hand = hand,
            discardPile = RiichiDiscardPile(),
            playerRuleState = RiichiPlayerState()
        )
        val table = FakeTableStateFactory.create(players = listOf(player), dynamicRuleState = null)

        assertNull(module.declareRiichi(table, player, discardResult))
    }

    /**
     * 驗證玩家已立直且仍在一發窗口內時，摸牌會清除一發資格。
     */
    @Test
    fun `test onPlayerDrew clears ippatsu when in ippatsu window`() {
        val player = FakeMahjongPlayerFactory.create(
            playerRuleState = RiichiPlayerState(riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East), isIppatsu = true)
        )

        val result = module.onPlayerDrew(player)

        val riichiState = result.playerRuleState as RiichiPlayerState
        assertFalse(riichiState.isIppatsu)
        assertTrue(riichiState.isRiichi, "Riichi itself should remain in effect.")
    }

    /**
     * 驗證玩家不在一發窗口內（未立直或一發已失效）時，摸牌不應變動玩家實例。
     */
    @Test
    fun `test onPlayerDrew is a no-op when not in ippatsu window`() {
        val player = FakeMahjongPlayerFactory.create(playerRuleState = RiichiPlayerState())

        val result = module.onPlayerDrew(player)

        assertSame(player, result)
    }

    /**
     * 驗證鳴牌事件會清除所有仍在一發窗口內的玩家的一發資格，不影響其他玩家。
     */
    @Test
    fun `test onMeldClaimed clears ippatsu for all affected players`() {
        val ippatsuPlayer = FakeMahjongPlayerFactory.create(
            playerRuleState = RiichiPlayerState(riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East), isIppatsu = true)
        )
        val nonRiichiPlayer = FakeMahjongPlayerFactory.create(playerRuleState = null)

        val result = module.onMeldClaimed(listOf(ippatsuPlayer, nonRiichiPlayer))

        assertFalse((result[0].playerRuleState as RiichiPlayerState).isIppatsu)
        assertSame(nonRiichiPlayer, result[1])
    }

    /**
     * 驗證碰第三組三元牌、湊齊大三元時，會將包牌責任寫入玩家的規則狀態。
     */
    @Test
    fun `test applyPaoLiabilityIfTriggered writes liability when triggered`() {
        val hand = FakeHandFactory.create(
            listOf(
                Tile.Honor.Red, Tile.Honor.Red, Tile.Honor.Red,
                Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green
            )
        )
        val player = FakeMahjongPlayerFactory.create(hand = hand, playerRuleState = RiichiPlayerState())
        val calledTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)

        val result = module.applyPaoLiabilityIfTriggered(player, calledTile, RelativeDirection.Left)

        val riichiState = result.playerRuleState as RiichiPlayerState
        assertEquals(PaoLiability(PaoYaku.Daisangen, RelativeDirection.Left), riichiState.paoLiability)
    }

    /**
     * 驗證未觸發包牌責任時，玩家實例不應變動。
     */
    @Test
    fun `test applyPaoLiabilityIfTriggered is a no-op when not triggered`() {
        val player = FakeMahjongPlayerFactory.create(playerRuleState = RiichiPlayerState())
        val calledTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))

        val result = module.applyPaoLiabilityIfTriggered(player, calledTile, RelativeDirection.Left)

        assertSame(player, result)
    }

    /**
     * 驗證玩家的規則狀態不是 [RiichiPlayerState] 時，回傳玩家本身（防呆）。
     */
    @Test
    fun `test applyPaoLiabilityIfTriggered is a no-op when player rule state is not riichi`() {
        val player = FakeMahjongPlayerFactory.create(playerRuleState = null)
        val calledTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)

        val result = module.applyPaoLiabilityIfTriggered(player, calledTile, RelativeDirection.Left)

        assertSame(player, result)
    }
}