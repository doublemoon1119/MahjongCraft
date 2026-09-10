package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailability
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 立直麻將打牌分析器之單元測試。
 *
 * 測試內容涵蓋只回傳仍聽牌的捨牌候選、等待牌剩餘張數估算，以及三種振聽狀態的優先順序。
 *
 * @see RiichiDiscardReadinessAnalyzer
 */
class RiichiDiscardReadinessAnalyzerTest {

    private val shantenCalculator = RiichiShantenCalculator()
    private val legalActionValidator = RiichiLegalActionValidator(
        shantenCalculator = shantenCalculator,
        handValueCalculator = RiichiHandValueCalculator(),
        contextCalculator = RiichiHandValueContextCalculator(RiichiRuleConfig()),
    )
    private val analyzer = RiichiDiscardReadinessAnalyzer(shantenCalculator, legalActionValidator)

    private val threeSou = Tile.Numeric(Tile.Suit.Bamboo, 3)
    private val sixSou = Tile.Numeric(Tile.Suit.Bamboo, 6)

    /**
     * 123456789m（三組順子）＋55p（對子）＋4s5s（兩面聽 3s/6s）加一張孤立的東風——三組順子與對子皆已
     * 完成、花色彼此不重疊，唯一能維持聽牌的捨牌只有這張孤立東風，不會有其他捨牌候選誤判成聽牌。
     */
    private val floatingTile = Tile.Honor.East
    private val tenpaiTiles = listOf(
        Tile.Numeric(Tile.Suit.Character, 1),
        Tile.Numeric(Tile.Suit.Character, 2),
        Tile.Numeric(Tile.Suit.Character, 3),
        Tile.Numeric(Tile.Suit.Character, 4),
        Tile.Numeric(Tile.Suit.Character, 5),
        Tile.Numeric(Tile.Suit.Character, 6),
        Tile.Numeric(Tile.Suit.Character, 7),
        Tile.Numeric(Tile.Suit.Character, 8),
        Tile.Numeric(Tile.Suit.Character, 9),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Dot, 5),
        Tile.Numeric(Tile.Suit.Bamboo, 4),
        Tile.Numeric(Tile.Suit.Bamboo, 5),
    )

    /**
     * 驗證只有打出孤立浮牌才會維持聽牌，其餘捨牌候選（會破壞既有面子）都不列入分析結果。
     */
    @Test
    fun `test analyze only returns tenpai candidate for the floating tile`() {
        val hand = FakeHandFactory.create(tenpaiTiles + floatingTile)
        val player = FakeMahjongPlayerFactory.create(hand = hand)
        val tableState = FakeTableStateFactory.create(players = listOf(player), config = RiichiRuleConfig())

        val analyses = analyzer.analyze(tableState, player)

        val floatingTileId = hand.standingTiles.first { it.tile == floatingTile }.id
        assertEquals(listOf(floatingTileId), analyses.map { it.discardTileId })
    }

    /**
     * 驗證等待牌剩餘張數會扣除自己手牌中已持有的張數；沒有振聽因素時 statusIndicatorId 應為 null。
     */
    @Test
    fun `test analyze computes waiting tile remaining count and no furiten status`() {
        val hand = FakeHandFactory.create(tenpaiTiles + floatingTile)
        val player = FakeMahjongPlayerFactory.create(hand = hand)
        val tableState = FakeTableStateFactory.create(players = listOf(player), config = RiichiRuleConfig())

        val analysis = analyzer.analyze(tableState, player).single()

        val threeSouWait = analysis.waitingTiles.single { it.tile == threeSou }
        // 手牌與他家都沒有額外的 3s，牌山最多還剩全部 4 張。
        assertEquals(4, threeSouWait.remainingCount)
        assertNull(analysis.statusIndicatorId)
    }

    /**
     * 驗證他家牌河／副露中可見的等待牌會一併計入，讓剩餘張數進一步減少。
     */
    @Test
    fun `test analyze reduces remaining count for tiles visible in other players discards`() {
        val hand = FakeHandFactory.create(tenpaiTiles + floatingTile)
        val player = FakeMahjongPlayerFactory.create(hand = hand)
        val otherPlayer = FakeMahjongPlayerFactory.create(
            discardPile = FakeDiscardPile().discardTile(FakeIdentifiedTileFactory.create(threeSou)),
        )
        val tableState = FakeTableStateFactory.create(players = listOf(player, otherPlayer), config = RiichiRuleConfig())

        val analysis = analyzer.analyze(tableState, player).single()

        val threeSouWait = analysis.waitingTiles.single { it.tile == threeSou }
        assertEquals(3, threeSouWait.remainingCount)
    }

    /**
     * 驗證捨牌後若等待牌先前已被自己打出過，狀態應標記為捨張振聽。
     */
    @Test
    fun `test analyze reports discard furiten when a wait was already discarded`() {
        val hand = FakeHandFactory.create(tenpaiTiles + floatingTile)
        val player = FakeMahjongPlayerFactory.create(
            hand = hand,
            discardPile = FakeDiscardPile().discardTile(FakeIdentifiedTileFactory.create(threeSou)),
        )
        val tableState = FakeTableStateFactory.create(players = listOf(player), config = RiichiRuleConfig())

        val analysis = analyzer.analyze(tableState, player).single()

        assertEquals("mahjongcraft:discard_furiten", analysis.statusIndicatorId)
    }

    /**
     * 驗證捨牌後若等待牌是本巡放過的牌，狀態應標記為同巡振聽。
     */
    @Test
    fun `test analyze reports temporary furiten when a wait was passed this turn`() {
        val hand = FakeHandFactory.create(tenpaiTiles + floatingTile)
        val player = FakeMahjongPlayerFactory.create(hand = hand)
            .copy(passedTilesInRound = setOf(sixSou))
        val tableState = FakeTableStateFactory.create(players = listOf(player), config = RiichiRuleConfig())

        val analysis = analyzer.analyze(tableState, player).single()

        assertEquals("mahjongcraft:temporary_furiten", analysis.statusIndicatorId)
    }

    /**
     * 驗證玩家已處於永久振聽時，不論等待牌本身是否觸發捨張／同巡振聽，狀態一律優先標記為永久振聽。
     */
    @Test
    fun `test analyze reports permanent furiten regardless of other conditions`() {
        val hand = FakeHandFactory.create(tenpaiTiles + floatingTile)
        val player = FakeMahjongPlayerFactory.create(
            hand = hand,
            playerRuleState = RiichiPlayerState(isPermanentlyFuriten = true),
        )
        val tableState = FakeTableStateFactory.create(players = listOf(player), config = RiichiRuleConfig())

        val analysis = analyzer.analyze(tableState, player).single()

        assertEquals("mahjongcraft:permanent_furiten", analysis.statusIndicatorId)
    }

    /**
     * 驗證等待牌包含兩面聽的 3s 與 6s（實際完整聽牌組合由 [RiichiShantenCalculator] 自行決定，這裡只
     * 驗證分析器有把計算結果如實轉為等待牌列表，不重複驗證向聽演算法本身）。
     */
    @Test
    fun `test analyze includes both ryanmen wait tiles`() {
        val hand = FakeHandFactory.create(tenpaiTiles + floatingTile)
        val player = FakeMahjongPlayerFactory.create(hand = hand)
        val tableState = FakeTableStateFactory.create(players = listOf(player), config = RiichiRuleConfig())

        val analysis = analyzer.analyze(tableState, player).single()

        assertTrue(analysis.waitingTiles.any { it.tile == threeSou })
        assertTrue(analysis.waitingTiles.any { it.tile == sixSou })
    }

    /**
     * 驗證每張等待牌的 [WaitingTileAvailability.winAvailability] 都是命名字串（規則中立設計，見該
     * 屬性 KDoc），不是任何規則專屬型別的 toString() 結果。
     */
    @Test
    fun `test analyze produces namespaced win availability ids`() {
        val hand = FakeHandFactory.create(tenpaiTiles + floatingTile)
        val player = FakeMahjongPlayerFactory.create(hand = hand)
        val tableState = FakeTableStateFactory.create(players = listOf(player), config = RiichiRuleConfig())

        val analysis = analyzer.analyze(tableState, player).single()

        assertTrue(analysis.waitingTiles.isNotEmpty())
        analysis.waitingTiles.forEach { assertTrue(it.winAvailability.startsWith("mahjongcraft:win_")) }
    }

    /**
     * 驗證一般捨牌的無役單騎仍只可自摸，而玩家明確選擇立直後，同一候選會以正式立直狀態投影為可榮和。
     */
    @Test
    fun `test riichi action analysis projects declaration yaku`() {
        val completedMelds = listOf(
            Meld(
                MeldType.CLOSED_KAN,
                List(4) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1)) },
                sourceDirection = RelativeDirection.Self,
            ),
        )
        val hand = Hand(
            tiles = listOf(
                Tile.Numeric(Tile.Suit.Character, 2),
                Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Dot, 4),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Numeric(Tile.Suit.Dot, 6),
                Tile.Numeric(Tile.Suit.Bamboo, 7),
                Tile.Numeric(Tile.Suit.Bamboo, 8),
                Tile.Numeric(Tile.Suit.Bamboo, 9),
                Tile.Numeric(Tile.Suit.Dot, 5),
                Tile.Honor.East,
            ).map(FakeIdentifiedTileFactory::create),
            melds = completedMelds,
        )
        val player = FakeMahjongPlayerFactory.create(
            hand = hand,
            discardPile = RiichiDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.North)),
            playerRuleState = RiichiPlayerState(),
        )
        val tableState = FakeTableStateFactory.create(
            players = listOf(player),
            tileWall = TileWall(List(20) { FakeIdentifiedTileFactory.create(Tile.Honor.White) }),
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
        )
        val floatingTileId = hand.standingTiles.single { it.tile == Tile.Honor.East }.id

        val ordinary = analyzer.analyze(tableState, player).single { it.discardTileId == floatingTileId }
        val projected = analyzer.analyzeForAction(tableState, player, RIICHI_GAME_ACTION)
            .single { it.discardTileId == floatingTileId }

        assertEquals("mahjongcraft:win_tsumo_only", ordinary.waitingTiles.single().winAvailability)
        assertEquals("mahjongcraft:win_available", projected.waitingTiles.single().winAvailability)
    }
}
