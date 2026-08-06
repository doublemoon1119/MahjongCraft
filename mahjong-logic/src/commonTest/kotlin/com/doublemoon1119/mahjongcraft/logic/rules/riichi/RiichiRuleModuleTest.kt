package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.ExhaustiveDrawSettlementResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.WinSettlementResult
import com.doublemoon1119.mahjongcraft.logic.table.Wind
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
import kotlin.uuid.Uuid

/**
 * 針對 [RiichiRuleModule] 進行的單元測試。
 *
 * 驗證該模組是否能針對日本麻將規則正確生產對應的領域層組件。
 */
class RiichiRuleModuleTest {

    private val module: MahjongRuleModule<RiichiRuleConfig> = RiichiRuleModule(
        id = "mahjongcraft:riichi",
        config = RiichiRuleConfig(),
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
            playerRuleState = RiichiPlayerState(),
        ).copy(score = 25000)
        val table = FakeTableStateFactory.create(
            players = listOf(player),
            config = module.config,
            dynamicRuleState = RiichiDynamicState(),
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
            playerRuleState = RiichiPlayerState(),
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
            playerRuleState = RiichiPlayerState(),
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
            playerRuleState = RiichiPlayerState(riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East), isIppatsu = true),
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
            playerRuleState = RiichiPlayerState(riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East), isIppatsu = true),
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
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Red,
                Tile.Honor.Green,
                Tile.Honor.Green,
                Tile.Honor.Green,
            ),
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

    /**
     * 手牌：中中、發發發、白白白、123m、55p（大三元役滿，13 張立牌），供自摸結算測試共用。
     */
    private fun daisangenTiles() = listOf(
        Tile.Honor.Red, Tile.Honor.Red,
        Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
        Tile.Honor.White, Tile.Honor.White, Tile.Honor.White,
        Tile.Numeric(Tile.Suit.Character, 1),
        Tile.Numeric(Tile.Suit.Character, 2),
        Tile.Numeric(Tile.Suit.Character, 3),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Dot, 5),
    )

    /**
     * 驗證莊家自摸大三元時，其餘三位閒家平均分攤點數。
     */
    @Test
    fun `test declareTsumo splits payment evenly among three non-dealers when winner is dealer`() {
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val winner = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }, lastDrawn = winningTile),
            // 已經打過一輪牌，確保不是第一巡，避免誤觸天和/地和疊加成雙倍役滿
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        )
        val south = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
        val west = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST)
        val north = FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH)
        val table = FakeTableStateFactory.create(players = listOf(winner, south, west, north), config = module.config)

        val result = module.declareTsumo(table, winner)

        assertEquals(
            WinSettlementResult(
                totalGained = 48000,
                paymentsByPlayerId = mapOf(south.id to 16000, west.id to 16000, north.id to 16000),
            ),
            result,
        )
    }

    /**
     * 驗證閒家自摸大三元時，莊家與另外兩位閒家支付不同金額。
     */
    @Test
    fun `test declareTsumo charges dealer more than the other two non-dealers when winner is non-dealer`() {
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val dealer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val winner = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }, lastDrawn = winningTile),
            // 已經打過一輪牌，確保不是第一巡，避免誤觸天和/地和疊加成雙倍役滿
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        )
        val west = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST)
        val north = FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH)
        val table = FakeTableStateFactory.create(players = listOf(dealer, winner, west, north), config = module.config)

        val result = module.declareTsumo(table, winner)

        assertEquals(
            WinSettlementResult(
                totalGained = 32000,
                paymentsByPlayerId = mapOf(dealer.id to 16000, west.id to 8000, north.id to 8000),
            ),
            result,
        )
    }

    /**
     * 驗證包牌責任已成立時，只有包牌責任者（依 [PaoLiability.direction] 解析回實際玩家）付款，
     * 其餘玩家完全不出現在結算結果中。
     */
    @Test
    fun `test declareTsumo resolves pao payer via relative direction and no other player pays`() {
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val paoPlayer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val winner = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }, lastDrawn = winningTile),
            // 已經打過一輪牌，確保不是第一巡，避免誤觸天和/地和疊加成雙倍役滿
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(paoLiability = PaoLiability(PaoYaku.Daisangen, RelativeDirection.Left)),
        )
        val other1 = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST)
        val other2 = FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH)
        // paoPlayer 排在 winner 前一位（座位順序），故對 winner 而言方位為 Left，與上方宣告的包牌責任相符
        val table = FakeTableStateFactory.create(players = listOf(paoPlayer, winner, other1, other2), config = module.config)

        val result = module.declareTsumo(table, winner)

        assertEquals(WinSettlementResult(totalGained = 32000, paymentsByPlayerId = mapOf(paoPlayer.id to 32000)), result)
    }

    /**
     * 驗證玩家尚未摸牌（hand.lastDrawn 為 null）時，自摸結算回傳 null（防呆）。
     */
    @Test
    fun `test declareTsumo returns null when player has not drawn`() {
        val player = FakeMahjongPlayerFactory.create(playerRuleState = RiichiPlayerState())
        val table = FakeTableStateFactory.create(players = listOf(player), config = module.config)

        assertNull(module.declareTsumo(table, player))
    }

    /**
     * 驗證莊家榮和大三元時，放銃者一人支付全額。
     */
    @Test
    fun `test declareRon charges the discarder the full amount when winner is dealer`() {
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val winner = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }),
            // 已經打過一輪牌，確保不是第一巡，避免誤觸天和/地和疊加成雙倍役滿
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        )
        val discarder = FakeMahjongPlayerFactory.create(initialSeat = Wind.SOUTH)
        val west = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST)
        val north = FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH)
        val table = FakeTableStateFactory.create(players = listOf(winner, discarder, west, north), config = module.config)

        val result = module.declareRon(table, winner, winningTile, discarderId = discarder.id)

        assertEquals(
            WinSettlementResult(totalGained = 48000, paymentsByPlayerId = mapOf(discarder.id to 48000)),
            result,
        )
    }

    /**
     * 驗證閒家榮和大三元時，放銃者一人支付全額（金額比莊家榮和少）。
     */
    @Test
    fun `test declareRon charges the discarder the full amount when winner is non-dealer`() {
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val dealer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val winner = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }),
            // 已經打過一輪牌，確保不是第一巡，避免誤觸天和/地和疊加成雙倍役滿
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(),
        )
        val discarder = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST)
        val north = FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH)
        val table = FakeTableStateFactory.create(players = listOf(dealer, winner, discarder, north), config = module.config)

        val result = module.declareRon(table, winner, winningTile, discarderId = discarder.id)

        assertEquals(
            WinSettlementResult(totalGained = 32000, paymentsByPlayerId = mapOf(discarder.id to 32000)),
            result,
        )
    }

    /**
     * 驗證包牌責任已成立、且包牌責任者與放銃者是不同人時，兩人平分點數。
     */
    @Test
    fun `test declareRon splits payment between discarder and pao-liable player`() {
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val paoPlayer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val winner = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }),
            // 已經打過一輪牌，確保不是第一巡，避免誤觸天和/地和疊加成雙倍役滿
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(paoLiability = PaoLiability(PaoYaku.Daisangen, RelativeDirection.Left)),
        )
        val discarder = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST)
        val other = FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH)
        // paoPlayer 排在 winner 前一位（座位順序），故對 winner 而言方位為 Left，與上方宣告的包牌責任相符
        val table = FakeTableStateFactory.create(players = listOf(paoPlayer, winner, discarder, other), config = module.config)

        val result = module.declareRon(table, winner, winningTile, discarderId = discarder.id)

        assertEquals(
            WinSettlementResult(
                totalGained = 32000,
                paymentsByPlayerId = mapOf(discarder.id to 16000, paoPlayer.id to 16000),
            ),
            result,
        )
    }

    /**
     * 驗證包牌責任者恰好就是放銃者本人時，歸戶成單一全額付款，而不是兩筆各半的付款
     * （避免兩筆同 key 的付款在合併時互相覆蓋掉一半金額）。
     */
    @Test
    fun `test declareRon collapses to a single full payment when pao-liable player is the discarder`() {
        val winningTile = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val paoPlayer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)
        val winner = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = daisangenTiles().map { FakeIdentifiedTileFactory.create(it) }),
            // 已經打過一輪牌，確保不是第一巡，避免誤觸天和/地和疊加成雙倍役滿
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South)),
            playerRuleState = RiichiPlayerState(paoLiability = PaoLiability(PaoYaku.Daisangen, RelativeDirection.Left)),
        )
        val other1 = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST)
        val other2 = FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH)
        val table = FakeTableStateFactory.create(players = listOf(paoPlayer, winner, other1, other2), config = module.config)

        // 包牌責任者（paoPlayer）這次剛好也是放銃者本人
        val result = module.declareRon(table, winner, winningTile, discarderId = paoPlayer.id)

        assertEquals(
            WinSettlementResult(totalGained = 32000, paymentsByPlayerId = mapOf(paoPlayer.id to 32000)),
            result,
        )
    }

    /**
     * 驗證場上有立直棒時，收供託會回傳正確金額（立直棒數 * 1000），且回傳的狀態立直棒數歸零。
     */
    @Test
    fun `test collectStickPot returns amount and resets stick count when sticks are on the table`() {
        val table = FakeTableStateFactory.create(
            players = listOf(FakeMahjongPlayerFactory.create()),
            config = module.config,
            dynamicRuleState = RiichiDynamicState(riichiStickCount = 3),
        )

        val result = module.collectStickPot(table)

        assertEquals(RiichiDynamicState(riichiStickCount = 0) to 3000, result)
    }

    /**
     * 驗證場上沒有立直棒時，收供託仍回傳非 null（金額 0、狀態不變）——null 專門用來表示
     * 「這個規則根本沒有供託機制」，不跟「目前沒有供託」混用。
     */
    @Test
    fun `test collectStickPot returns zero amount when there are no sticks on the table`() {
        val table = FakeTableStateFactory.create(
            players = listOf(FakeMahjongPlayerFactory.create()),
            config = module.config,
            dynamicRuleState = RiichiDynamicState(riichiStickCount = 0),
        )

        val result = module.collectStickPot(table)

        assertEquals(RiichiDynamicState(riichiStickCount = 0) to 0, result)
    }

    /**
     * 聽牌手牌：1112345678999m（聽 1m 對倒），供 [declareExhaustiveDraw] 測試共用。
     */
    private fun tenpaiHand() = FakeHandFactory.create(
        listOf(
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 2),
            Tile.Numeric(Tile.Suit.Character, 3),
            Tile.Numeric(Tile.Suit.Character, 4),
            Tile.Numeric(Tile.Suit.Character, 5),
            Tile.Numeric(Tile.Suit.Character, 6),
            Tile.Numeric(Tile.Suit.Character, 7),
            Tile.Numeric(Tile.Suit.Character, 8),
            Tile.Numeric(Tile.Suit.Character, 9),
            Tile.Numeric(Tile.Suit.Character, 9),
            Tile.Numeric(Tile.Suit.Character, 9),
        ),
    )

    /**
     * 明顯遠離聽牌的手牌（13 張互不相干的孤立牌），供 [declareExhaustiveDraw] 測試共用。
     */
    private fun notTenpaiHand() = FakeHandFactory.create(
        listOf(
            Tile.Numeric(Tile.Suit.Character, 1),
            Tile.Numeric(Tile.Suit.Character, 4),
            Tile.Numeric(Tile.Suit.Character, 7),
            Tile.Numeric(Tile.Suit.Dot, 1),
            Tile.Numeric(Tile.Suit.Dot, 4),
            Tile.Numeric(Tile.Suit.Dot, 7),
            Tile.Numeric(Tile.Suit.Bamboo, 1),
            Tile.Numeric(Tile.Suit.Bamboo, 4),
            Tile.Numeric(Tile.Suit.Bamboo, 7),
            Tile.Honor.East,
            Tile.Honor.South,
            Tile.Honor.West,
            Tile.Honor.North,
        ),
    )

    /**
     * 全為么九牌、皆未被鳴走的牌河，成立流局滿貫的必要條件，供 [declareExhaustiveDraw] 測試共用。
     */
    private fun allYaochuuDiscardPile() = RiichiDiscardPile()
        .discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.East))
        .discardTile(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)))
        .discardTile(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 9)))

    /**
     * 驗證無人聽牌時不進行任何點數交換。
     */
    @Test
    fun `test declareExhaustiveDraw no exchange when nobody is tenpai`() {
        val players = List(4) { FakeMahjongPlayerFactory.create(hand = notTenpaiHand()) }
        val table = FakeTableStateFactory.create(players = players, config = module.config)

        val result = module.declareExhaustiveDraw(table)

        assertEquals(
            ExhaustiveDrawSettlementResult(
                reason = RiichiExhaustiveDrawReason.Normal,
                tenpaiPlayerIds = emptySet(),
                nagashiManganPlayerIds = emptySet(),
                scoreDeltas = emptyMap(),
            ),
            result,
        )
    }

    /**
     * 驗證全員聽牌時不進行任何點數交換。
     */
    @Test
    fun `test declareExhaustiveDraw no exchange when everyone is tenpai`() {
        val players = List(4) { FakeMahjongPlayerFactory.create(hand = tenpaiHand()) }
        val table = FakeTableStateFactory.create(players = players, config = module.config)

        val result = module.declareExhaustiveDraw(table)

        assertEquals(players.map { it.id }.toSet(), result?.tenpaiPlayerIds)
        assertEquals(emptyMap<Uuid, Int>(), result?.scoreDeltas)
    }

    /**
     * 驗證恰好一人聽牌時，聽牌者從其餘三家各收 1000 點（總額 3000）。
     */
    @Test
    fun `test declareExhaustiveDraw single tenpai player collects from the other three`() {
        val tenpaiPlayer = FakeMahjongPlayerFactory.create(hand = tenpaiHand())
        val notenPlayers = List(3) { FakeMahjongPlayerFactory.create(hand = notTenpaiHand()) }
        val table = FakeTableStateFactory.create(players = listOf(tenpaiPlayer) + notenPlayers, config = module.config)

        val result = module.declareExhaustiveDraw(table)

        assertEquals(setOf(tenpaiPlayer.id), result?.tenpaiPlayerIds)
        val expectedDeltas = mapOf(tenpaiPlayer.id to 3000) + notenPlayers.associate { it.id to -1000 }
        assertEquals(expectedDeltas, result?.scoreDeltas)
    }

    /**
     * 驗證恰好兩人聽牌時，兩位不聽者各支付 1500 點，兩位聽牌者各收 1500 點。
     */
    @Test
    fun `test declareExhaustiveDraw two tenpai players split evenly with two noten players`() {
        val tenpaiPlayers = List(2) { FakeMahjongPlayerFactory.create(hand = tenpaiHand()) }
        val notenPlayers = List(2) { FakeMahjongPlayerFactory.create(hand = notTenpaiHand()) }
        val table = FakeTableStateFactory.create(players = tenpaiPlayers + notenPlayers, config = module.config)

        val result = module.declareExhaustiveDraw(table)

        val expectedDeltas = tenpaiPlayers.associate { it.id to 1500 } + notenPlayers.associate { it.id to -1500 }
        assertEquals(expectedDeltas, result?.scoreDeltas)
    }

    /**
     * 驗證恰好三人聽牌時，唯一不聽者支付 3000 點，由三位聽牌者各收 1000 點。
     */
    @Test
    fun `test declareExhaustiveDraw three tenpai players collect from the single noten player`() {
        val tenpaiPlayers = List(3) { FakeMahjongPlayerFactory.create(hand = tenpaiHand()) }
        val notenPlayer = FakeMahjongPlayerFactory.create(hand = notTenpaiHand())
        val table = FakeTableStateFactory.create(players = tenpaiPlayers + notenPlayer, config = module.config)

        val result = module.declareExhaustiveDraw(table)

        val expectedDeltas = tenpaiPlayers.associate { it.id to 1000 } + mapOf(notenPlayer.id to -3000)
        assertEquals(expectedDeltas, result?.scoreDeltas)
    }

    /**
     * 驗證莊家成立流局滿貫時，視為自摸滿貫由其餘三家各付 4000 點（總額 12000），
     * 且即使其他玩家不聽也不進行不聽罰符收授。
     */
    @Test
    fun `test declareExhaustiveDraw dealer nagashi mangan charges 4000 from each of the other three`() {
        val dealer = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = notTenpaiHand(),
            discardPile = allYaochuuDiscardPile(),
        )
        val others = List(3) { FakeMahjongPlayerFactory.create(hand = notTenpaiHand()) }
        val table = FakeTableStateFactory.create(players = listOf(dealer) + others, config = module.config)

        val result = module.declareExhaustiveDraw(table)

        assertEquals(setOf(dealer.id), result?.nagashiManganPlayerIds)
        assertTrue(dealer.id in requireNotNull(result).tenpaiPlayerIds)
        val expectedDeltas = mapOf(dealer.id to 12000) + others.associate { it.id to -4000 }
        assertEquals(expectedDeltas, result.scoreDeltas)
    }

    /**
     * 驗證閒家成立流局滿貫時，視為自摸滿貫，莊家付 4000 點、其餘兩位閒家各付 2000 點（總額 8000）。
     */
    @Test
    fun `test declareExhaustiveDraw non-dealer nagashi mangan charges dealer double`() {
        val dealer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST, hand = notTenpaiHand())
        val achiever = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            hand = notTenpaiHand(),
            discardPile = allYaochuuDiscardPile(),
        )
        val west = FakeMahjongPlayerFactory.create(initialSeat = Wind.WEST, hand = notTenpaiHand())
        val north = FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH, hand = notTenpaiHand())
        val table = FakeTableStateFactory.create(players = listOf(dealer, achiever, west, north), config = module.config)

        val result = module.declareExhaustiveDraw(table)

        assertEquals(setOf(achiever.id), result?.nagashiManganPlayerIds)
        val expectedDeltas = mapOf(achiever.id to 8000, dealer.id to -4000, west.id to -2000, north.id to -2000)
        assertEquals(expectedDeltas, result?.scoreDeltas)
    }

    /**
     * 驗證牌河中有一張么九牌被鳴走時，流局滿貫不成立，退回一般聽牌/不聽罰符路徑。
     */
    @Test
    fun `test declareExhaustiveDraw nagashi mangan does not apply when a discard was taken`() {
        val discardPile = allYaochuuDiscardPile().takeLast()
        val player = FakeMahjongPlayerFactory.create(hand = tenpaiHand(), discardPile = discardPile)
        val notenPlayers = List(3) { FakeMahjongPlayerFactory.create(hand = notTenpaiHand()) }
        val table = FakeTableStateFactory.create(players = listOf(player) + notenPlayers, config = module.config)

        val result = module.declareExhaustiveDraw(table)

        assertEquals(emptySet<Uuid>(), result?.nagashiManganPlayerIds)
        assertEquals(mapOf(player.id to 3000) + notenPlayers.associate { it.id to -1000 }, result?.scoreDeltas)
    }

    /**
     * 驗證牌河中含有非么九牌時，流局滿貫不成立，退回一般聽牌/不聽罰符路徑。
     */
    @Test
    fun `test declareExhaustiveDraw nagashi mangan does not apply when a non-yaochuu tile was discarded`() {
        val discardPile = RiichiDiscardPile()
            .discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.East))
            .discardTile(FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5)))
        val player = FakeMahjongPlayerFactory.create(hand = tenpaiHand(), discardPile = discardPile)
        val notenPlayers = List(3) { FakeMahjongPlayerFactory.create(hand = notTenpaiHand()) }
        val table = FakeTableStateFactory.create(players = listOf(player) + notenPlayers, config = module.config)

        val result = module.declareExhaustiveDraw(table)

        assertEquals(emptySet<Uuid>(), result?.nagashiManganPlayerIds)
        assertEquals(mapOf(player.id to 3000) + notenPlayers.associate { it.id to -1000 }, result?.scoreDeltas)
    }

    /**
     * 驗證兩位非莊家同時成立流局滿貫時，各自的自摸滿貫結算會正確加總到同一份 `scoreDeltas`——
     * 兩位成立者彼此之間互為對方結算裡的付款方，需要疊加而非互相覆蓋。
     */
    @Test
    fun `test declareExhaustiveDraw aggregates deltas when multiple non-dealers achieve nagashi mangan`() {
        val dealer = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST, hand = notTenpaiHand())
        val south = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.SOUTH,
            hand = notTenpaiHand(),
            discardPile = allYaochuuDiscardPile(),
        )
        val west = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.WEST,
            hand = notTenpaiHand(),
            discardPile = allYaochuuDiscardPile(),
        )
        val north = FakeMahjongPlayerFactory.create(initialSeat = Wind.NORTH, hand = notTenpaiHand())
        val table = FakeTableStateFactory.create(players = listOf(dealer, south, west, north), config = module.config)

        val result = module.declareExhaustiveDraw(table)

        assertEquals(setOf(south.id, west.id), result?.nagashiManganPlayerIds)
        val expectedDeltas = mapOf(
            dealer.id to -8000,
            south.id to 6000,
            west.id to 6000,
            north.id to -4000,
        )
        assertEquals(expectedDeltas, result?.scoreDeltas)
    }
}
