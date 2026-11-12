package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds
import com.doublemoon1119.mahjongcraft.logic.module.LegalActionAutomaticControlPolicy
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.MeldCompletedContext
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.layout.TaiwanPhysicalWallLayoutPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.layout.TaiwanWallLayout
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.opening.TaiwanWallOpeningPolicy
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionContext
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionDecision
import com.doublemoon1119.mahjongcraft.logic.table.MatchRoundTransition
import com.doublemoon1119.mahjongcraft.logic.table.NoOpWallRevealPolicy
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 針對 [TaiwanRuleModule] 進行的單元測試。
 *
 * 驗證該模組是否能針對台灣麻將規則正確生產對應的領域層組件。
 */
class TaiwanRuleModuleTest {

    private val taiwanModule = TaiwanRuleModule(
        id = "mahjongcraft:taiwan",
        config = TaiwanRuleConfig(),
    )
    private val module: MahjongRuleModule<TaiwanRuleConfig> = taiwanModule

    /** 台麻支援與日麻相同的三種本局自動操作，並使用只看合法動作的共用判斷。 */
    @Test
    fun `test automatic controls match riichi and use the shared policy`() {
        assertEquals(
            setOf(BuiltInAutomaticControlIds.AUTO_WIN, BuiltInAutomaticControlIds.DECLINE_CALLS, BuiltInAutomaticControlIds.AUTO_TSUMOGIRI),
            module.getSupportedAutomaticControlIds(),
        )
        assertSame(LegalActionAutomaticControlPolicy, module.createAutomaticControlPolicy())
    }

    /** 未提供牌牆公開政策的規則應安全使用 no-op，不套用日麻行為。 */
    @Test
    fun `test wall reveal policy defaults to no-op`() {
        assertSame(NoOpWallRevealPolicy, module.createWallRevealPolicy())
    }

    /**
     * 驗證建立的牌山工廠是否為台灣麻將實作。
     */
    @Test
    fun `test create wall factory returns taiwan implementation`() {
        val factory = module.createWallFactory(module.config.maxPlayers)
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
        assertTrue(module.createWallLayout(module.config.maxPlayers) is TaiwanWallLayout)
    }

    /** 驗證規則模組提供台灣麻將的保留區實體布局。 */
    @Test
    fun `test create physical wall layout policy returns taiwan implementation`() {
        assertIs<TaiwanPhysicalWallLayoutPolicy>(module.createPhysicalWallLayoutPolicy())
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
    @Test
    fun `test create hand value calculator returns taiwan implementation`() {
        assertTrue(module.createHandValueCalculator() is TaiwanHandValueCalculator)
    }

    /**
     * 驗證建立的手牌價值上下文計算機是否為台灣麻將實作。
     */
    @Test
    fun `test create hand value context calculator returns taiwan implementation`() {
        assertTrue(module.createHandValueContextCalculator() is TaiwanHandValueContextCalculator)
    }

    /**
     * 驗證台灣麻將目前沒有摸牌後需要清除的規則特有狀態，不做任何變更。
     */
    @Test
    fun `test onPlayerDrew changes nothing`() {
        val player = FakeMahjongPlayerFactory.create()

        assertTrue(module.onPlayerDrew(player).isEmpty)
    }

    /**
     * 驗證預設設定不會在副露或槓完成後改變規則狀態。
     */
    @Test
    fun `test onMeldCompleted changes nothing by default`() {
        val players = listOf(FakeMahjongPlayerFactory.create(), FakeMahjongPlayerFactory.create())

        assertTrue(module.onMeldCompleted(MeldCompletedContext(players, players, players.first().id, MeldType.Chi)).isEmpty())
    }

    /** 啟用選項後，暗槓前後等待牌相同時會解除動作者的過水。 */
    @Test
    fun `closed kan clears passed win when waits stay unchanged`() {
        val enabled = TaiwanRuleModule("mahjongcraft:taiwan", TaiwanRuleConfig(kanClearsPassedWin = true))
        val player = FakeMahjongPlayerFactory.create(
            hand = Hand(tiles = waitingTiles()),
            components = ComponentSet.EMPTY.with(TaiwanPlayerComponents.PASSED_WIN, Unit),
        )
        val players = listOf(player)

        val patch = enabled.onMeldCompleted(MeldCompletedContext(players, players, player.id, MeldType.ClosedKan)).getValue(player.id)

        assertNull(patch.applyTo(player.components, BuiltInComponentOwners.PLAYER)[TaiwanPlayerComponents.PASSED_WIN])
    }

    /** 啟用選項後，槓牌前後等待牌不同仍須保留過水。 */
    @Test
    fun `closed kan keeps passed win when waits change`() {
        val enabled = TaiwanRuleModule("mahjongcraft:taiwan", TaiwanRuleConfig(kanClearsPassedWin = true))
        val before = FakeMahjongPlayerFactory.create(
            hand = Hand(tiles = waitingTiles()),
            components = ComponentSet.EMPTY.with(TaiwanPlayerComponents.PASSED_WIN, Unit),
        )
        val after = before.copy(hand = Hand(tiles = waitingTiles().dropLast(1) + tile(Tile.Honor.East)))

        assertTrue(
            enabled.onMeldCompleted(
                MeldCompletedContext(
                    playersBefore = listOf(before),
                    playersAfter = listOf(after),
                    actorPlayerId = before.id,
                    meldType = MeldType.ClosedKan,
                ),
            ).isEmpty(),
        )
    }

    /** 即使選項已啟用，吃牌也不會額外解除過水。 */
    @Test
    fun `chi does not clear passed win`() {
        val enabled = TaiwanRuleModule("mahjongcraft:taiwan", TaiwanRuleConfig(kanClearsPassedWin = true))
        val player = FakeMahjongPlayerFactory.create(
            hand = Hand(tiles = waitingTiles()),
            components = ComponentSet.EMPTY.with(TaiwanPlayerComponents.PASSED_WIN, Unit),
        )
        val players = listOf(player)

        assertTrue(enabled.onMeldCompleted(MeldCompletedContext(players, players, player.id, MeldType.Chi)).isEmpty())
    }

    /**
     * 驗證台灣麻將目前沒有立直/供託這個機制，回傳 null。
     */
    @Test
    fun `test collectStickPot returns null`() {
        val table = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create()))

        assertNull(module.collectStickPot(table))
    }

    /** 驗證沒有覆寫連莊點數的規則即使有連莊次數也不產生任何付款。 */
    @Test
    fun `test resolveComboBonusPayments defaults to no payments`() {
        val winner = FakeMahjongPlayerFactory.create(Wind.EAST)
        val table = FakeTableStateFactory.create(players = listOf(winner, FakeMahjongPlayerFactory.create(Wind.SOUTH)), comboCount = 3)

        val payments = module.resolveComboBonusPayments(
            tableState = table,
            winnerId = winner.id,
            discarderId = null,
            resolution = null,
        )

        assertTrue(payments.isEmpty())
    }

    /**
     * 驗證台灣麻將目前沒有多家和判定為流局這個機制的具體流局原因型別，回傳 null。
     */
    @Test
    fun `test resolveMultiRonAbortiveDraw returns null`() {
        assertNull(module.resolveMultiRonAbortiveDraw())
    }

    /**
     * 驗證台灣麻將沒有寶牌概念，不覆寫 [MahjongRuleModule.isBonusTile]，固定回傳預設值 false。
     */
    @Test
    fun `test isBonusTile returns false`() {
        val tile = Tile.Numeric(Tile.Suit.Character, 5)

        assertFalse(module.isBonusTile(TileFaceView(tile), listOf(TileFaceView(Tile.Numeric(Tile.Suit.Character, 4)))))
    }

    /** 宣告時機決定宣告種類：莊家第一打為天聽，全桌沒有副露時閒家第一打為地聽，其他時候為一般宣告聽牌。 */
    @Test
    fun `declaration kind follows the declaring turn`() {
        val table = fourPlayerTable(waiterHand = Hand(tiles = waitingTiles(), lastDrawn = tile(Tile.Honor.West)))
        val dealer = table.players[0].copy(hand = table.players[1].hand)
        val waiter = table.players[1]

        assertEquals(TaiwanReadyKind.HEAVENLY, declaredKind(table, dealer))
        assertEquals(TaiwanReadyKind.EARTHLY, declaredKind(table, waiter))
        val afterDiscard = waiter.copy(discardPile = TaiwanDiscardPile().discardTile(tile(Tile.Honor.North)))
        assertEquals(TaiwanReadyKind.GENERAL, declaredKind(table, afterDiscard))
    }

    /** 放過胡牌後進入過水，天聽改為一般宣告聽牌；打出不能胡的牌後解除，打出聽的牌時不解除。 */
    @Test
    fun `declining a win starts passed win and a non-winning discard clears it`() {
        val heavenly = FakeMahjongPlayerFactory.create(components = ComponentSet.EMPTY.with(TaiwanPlayerComponents.READY_DECLARATION, TaiwanReadyKind.HEAVENLY))

        val declined = taiwanModule.onPlayerDeclinedWin(heavenly).applyTo(heavenly.components, BuiltInComponentOwners.PLAYER)

        assertEquals(Unit, declined[TaiwanPlayerComponents.PASSED_WIN])
        assertEquals(TaiwanReadyKind.GENERAL, declined[TaiwanPlayerComponents.READY_DECLARATION])

        val passed = FakeMahjongPlayerFactory.create(hand = Hand(tiles = waitingTiles()), components = ComponentSet.EMPTY.with(TaiwanPlayerComponents.PASSED_WIN, Unit))
        val nonWinning = passed.copy(discardPile = TaiwanDiscardPile().discardTile(tile(Tile.Honor.North)))
        val winning = passed.copy(discardPile = TaiwanDiscardPile().discardTile(tile(Tile.Suit.Bamboo, 9)))
        assertNull(taiwanModule.onPlayerDiscarded(nonWinning).applyTo(nonWinning.components, BuiltInComponentOwners.PLAYER)[TaiwanPlayerComponents.PASSED_WIN])
        assertTrue(taiwanModule.onPlayerDiscarded(winning).isEmpty)
    }

    /** 閒家自摸由其他三家各付一筆，莊家多付莊家台；放槍只由放槍者付款。 */
    @Test
    fun `self draw is paid by three players and a discard win by the discarder`() {
        val winningTile = tile(Tile.Suit.Bamboo, 9)
        val table = fourPlayerTable(waiterHand = Hand(tiles = waitingTiles(), lastDrawn = winningTile))
        val waiter = table.players[1]

        val tsumo = checkNotNull(taiwanModule.declareTsumo(table, waiter))
        assertEquals(table.players.filterNot { it.id == waiter.id }.map { it.id }.toSet(), tsumo.paymentsByPlayerId.keys)
        assertTrue(tsumo.paymentsByPlayerId.getValue(table.players[0].id) > tsumo.paymentsByPlayerId.getValue(table.players[2].id))

        val discarder = table.players[2].id
        val ron = checkNotNull(taiwanModule.declareRon(table, waiter.copy(hand = Hand(tiles = waitingTiles())), winningTile, discarder))
        assertEquals(setOf(discarder), ron.paymentsByPlayerId.keys)
    }

    /** 荒牌流局不付款；牌牆尚未摸完時不成立。 */
    @Test
    fun `exhaustive draw pays nothing once the wall is empty`() {
        val table = fourPlayerTable(waiterHand = Hand())

        assertNull(taiwanModule.declareExhaustiveDraw(table))
        val settled = checkNotNull(taiwanModule.declareExhaustiveDraw(table.copy(tileWall = TileWall(emptyList()))))
        assertTrue(settled.scoreDeltas.values.all { it == 0 })
    }

    /** 荒牌流局連莊；末局莊家連莊時繼續打，下莊時才結束整場。 */
    @Test
    fun `last round continues while the dealer repeats`() {
        val table = fourPlayerTable(waiterHand = Hand())
        val drawTransition = taiwanModule.resolveRoundTransition(
            RoundTransitionContext(
                tableState = table,
                outcomeId = "mahjongcraft:test_draw",
                classification = RoundCompletionClassification.EXHAUSTIVE_DRAW,
                beneficiaryPlayerIds = emptySet(),
                responsiblePlayerIds = emptySet(),
                exhaustiveDrawReason = TaiwanExhaustiveDrawReason.Normal,
            ),
        )
        assertEquals(RoundTransitionDirective.RepeatDealer, drawTransition)

        val policy = TaiwanMatchProgressionPolicy(totalRounds = 1)
        assertEquals(
            MatchProgressionDecision.ContinueMatch(MatchRoundTransition.RepeatCurrentRound),
            policy.decide(progression(table, RoundTransitionDirective.RepeatDealer)),
        )
        assertIs<MatchProgressionDecision.EndMatch>(policy.decide(progression(table, RoundTransitionDirective.PassToNextSeat)))
    }

    /**
     * 讓玩家打出剛摸入的牌並宣告聽牌，取得成立的宣告種類。
     *
     * @param table 宣告前的桌況。
     * @param player 宣告的玩家。
     * @return 宣告種類；宣告不成立時為 null。
     */
    private fun declaredKind(table: TableState, player: MahjongPlayer): TaiwanReadyKind? {
        val discard = checkNotNull(player.hand.discardById(checkNotNull(player.hand.lastDrawn).id))
        val effect = taiwanModule.declarationDiscardPolicy().resolve(table, player, TAIWAN_DECLARE_READY_ACTION, discard) ?: return null
        return effect.playerComponents.applyTo(player.components, BuiltInComponentOwners.PLAYER)[TaiwanPlayerComponents.READY_DECLARATION]
    }

    /**
     * 建立一局結束後的整場推進情境。
     *
     * @param table 本局結束時的桌況。
     * @param directive 本局的莊家推進決策。
     * @return 推進情境。
     */
    private fun progression(table: TableState, directive: RoundTransitionDirective) = MatchProgressionContext(
        tableState = table,
        completion = RoundCompletionSummary(
            outcomeId = "mahjongcraft:test_outcome",
            classification = RoundCompletionClassification.WIN,
            beneficiaryPlayerIds = emptySet(),
            responsiblePlayerIds = emptySet(),
            transitionDirective = directive,
            settledScoresByPlayerId = table.players.associate { it.id to it.score },
        ),
        rankedPlayerIds = table.players.map { it.id },
    )

    /**
     * 建立莊家為東家、第二位玩家持有 [waiterHand] 的四人台灣麻將桌況。
     *
     * @param waiterHand 第二位玩家的手牌。
     * @return 桌況。
     */
    private fun fourPlayerTable(waiterHand: Hand): TableState {
        val players = Wind.entries.mapIndexed { index, wind ->
            FakeMahjongPlayerFactory.create(wind, hand = if (index == 1) waiterHand else Hand(), discardPile = TaiwanDiscardPile())
        }
        return FakeTableStateFactory.create(
            players = players,
            config = TaiwanRuleConfig(),
            tileWall = TileWall(List(LIVE_TILES) { tile(Tile.Suit.Dot, 9) }),
        )
    }

    /**
     * 單騎九索聽牌的十六張手牌。
     *
     * @return 手牌。
     */
    private fun waitingTiles(): List<IdentifiedTile> = (1..9).map { tile(Tile.Suit.Character, it) } +
        (1..6).map { tile(Tile.Suit.Dot, it) } +
        tile(Tile.Suit.Bamboo, 9)

    /**
     * 建立數牌。
     *
     * @param suit 花色。
     * @param value 點數。
     * @return 牌張。
     */
    private fun tile(suit: Tile.Suit, value: Int): IdentifiedTile = FakeIdentifiedTileFactory.create(Tile.Numeric(suit, value))

    /**
     * 建立字牌。
     *
     * @param honor 字牌。
     * @return 牌張。
     */
    private fun tile(honor: Tile.Honor): IdentifiedTile = FakeIdentifiedTileFactory.create(honor)

    /** 測試常數。 */
    private companion object {
        /** 測試桌況的活牌張數。 */
        const val LIVE_TILES = 20
    }
}
