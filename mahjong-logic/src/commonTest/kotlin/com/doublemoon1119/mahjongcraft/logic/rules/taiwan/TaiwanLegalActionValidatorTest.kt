package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 台灣麻將合法動作、捨牌限制與過水的判定。 */
class TaiwanLegalActionValidatorTest {
    private val validator = TaiwanLegalActionValidator(TaiwanRuleConfig())

    /** 單騎九索聽牌的十六張手牌：三組萬子、兩組筒子順子與一張九索。 */
    private val waitingTiles: List<IdentifiedTile> = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9).map { tile(Tile.Suit.Character, it) } +
        listOf(1, 2, 3, 4, 5, 6).map { tile(Tile.Suit.Dot, it) } +
        tile(Tile.Suit.Bamboo, 9)

    /** 別家打出聽的牌時可以胡；過水期間不能胡。 */
    @Test
    fun `passed win blocks ron until cleared`() {
        val winning = tile(Tile.Suit.Bamboo, 9)
        val ready = table(waiter = player(Wind.SOUTH, Hand(tiles = waitingTiles)))

        assertTrue(GameAction.Ron(winning.id) in actionsOnDiscard(ready, winning))
        val passed = ready.withPlayer(1) { it.copy(components = ComponentSet.EMPTY.with(TaiwanPlayerComponents.PASSED_WIN, Unit)) }
        assertFalse(actionsOnDiscard(passed, winning).any { it is GameAction.Ron })
    }

    /** 過水期間也不能自摸。 */
    @Test
    fun `passed win blocks self draw`() {
        val winning = tile(Tile.Suit.Bamboo, 9)
        val hand = Hand(tiles = waitingTiles, lastDrawn = winning)
        val ready = table(waiter = player(Wind.SOUTH, hand), currentPlayerIndex = 1)

        assertTrue(GameAction.Tsumo in validator.getLegalActions(LegalActionContext.ownTurn(ready, ready.players[1].id)))
        val passed = ready.withPlayer(1) { it.copy(components = ComponentSet.EMPTY.with(TaiwanPlayerComponents.PASSED_WIN, Unit)) }
        assertFalse(GameAction.Tsumo in validator.getLegalActions(LegalActionContext.ownTurn(passed, passed.players[1].id)))
    }

    /** 宣告聽牌後不能碰別家的牌，但仍可以胡。 */
    @Test
    fun `declared ready player cannot claim but can win`() {
        val tiles = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9).map { tile(Tile.Suit.Character, it) } +
            listOf(1, 2, 3).map { tile(Tile.Suit.Dot, it) } +
            listOf(tile(Tile.Honor.Red), tile(Tile.Honor.Red), tile(Tile.Suit.Bamboo, 7), tile(Tile.Suit.Bamboo, 8))
        val waiter = player(Wind.SOUTH, Hand(tiles = tiles))
        val undeclared = table(waiter = waiter)
        val declared = table(waiter = waiter.copy(components = ComponentSet.EMPTY.with(TaiwanPlayerComponents.READY_DECLARATION, TaiwanReadyKind.GENERAL)))

        assertTrue(actionsOnDiscard(undeclared, tile(Tile.Honor.Red)).any { it is GameAction.Pon })
        assertFalse(actionsOnDiscard(declared, tile(Tile.Honor.Red)).any { it is GameAction.Pon })
        assertTrue(actionsOnDiscard(declared, tile(Tile.Suit.Bamboo, 9)).any { it is GameAction.Ron })
    }

    /** 不能槓上家打出的牌；別家打出時可以明槓。 */
    @Test
    fun `open kan is not offered on the left player's discard`() {
        val east = List(3) { tile(Tile.Honor.East) }
        val hand = Hand(tiles = east + waitingTiles.take(13))
        val state = table(waiter = player(Wind.SOUTH, hand))
        val discarded = tile(Tile.Honor.East)

        assertFalse(actionsOnDiscard(state, discarded, discarderIndex = 0).any { it is GameAction.Kan })
        assertTrue(actionsOnDiscard(state, discarded, discarderIndex = 2).any { it is GameAction.Kan })
    }

    /** 只能搶加槓，不能搶暗槓。 */
    @Test
    fun `only an added kan can be robbed`() {
        val winning = tile(Tile.Suit.Bamboo, 9)
        val state = table(waiter = player(Wind.SOUTH, Hand(tiles = waitingTiles)))
        val declarer = state.players[0].id

        fun robbing(type: GameAction.KanType) = validator.getLegalActions(
            context = LegalActionContext.declarationReaction(state, state.players[1].id, declarer, GameAction.Kan(type, winning.id, emptyList()), winning),
        )

        assertTrue(robbing(GameAction.KanType.ADDED_KAN).any { it is GameAction.Ron })
        assertFalse(robbing(GameAction.KanType.CLOSED_KAN).any { it is GameAction.Ron })
    }

    /** 吃牌後不能打出吃進的牌或順子另一頭的牌；碰牌後不能打出同一種牌。 */
    @Test
    fun `swap calling is forbidden after chi and pon`() {
        val called = tile(Tile.Suit.Character, 3)
        val chiMeld = Meld(
            type = MeldType.Chi,
            tiles = listOf(called, tile(Tile.Suit.Character, 4), tile(Tile.Suit.Character, 5)),
            sourceTile = called,
            sourceDirection = RelativeDirection.Left,
            publiclyRevealedTileIds = setOf(called.id),
            faceDownTileIds = emptySet(),
        )
        val sameKind = tile(Tile.Suit.Character, 3)
        val otherEnd = tile(Tile.Suit.Character, 6)
        val free = tile(Tile.Honor.North)
        val afterChi = player(Wind.SOUTH, Hand(tiles = listOf(sameKind, otherEnd, free), melds = listOf(chiMeld)))
            .recordAction(GameAction.Chi(called.id, chiMeld.tiles.drop(1).map { it.id }))

        assertEquals(setOf(free.id), TaiwanDiscardRestriction.discardableTileIds(afterChi))

        val onlyForbidden = afterChi.copy(hand = afterChi.hand.copy(tiles = listOf(sameKind, otherEnd)))
        assertNull(TaiwanDiscardRestriction.discardableTileIds(onlyForbidden))
    }

    /** 宣告聽牌後只能打出剛摸入的牌。 */
    @Test
    fun `declared ready player may only discard the drawn tile`() {
        val drawn = tile(Tile.Honor.West)
        val declared = player(Wind.SOUTH, Hand(tiles = waitingTiles, lastDrawn = drawn))
            .copy(components = ComponentSet.EMPTY.with(TaiwanPlayerComponents.READY_DECLARATION, TaiwanReadyKind.GENERAL))

        assertEquals(setOf(drawn.id), TaiwanDiscardRestriction.discardableTileIds(declared))
    }

    /** 打出後仍聽牌的牌才可以用來宣告聽牌。 */
    @Test
    fun `declare ready is offered with the discards that keep the hand ready`() {
        val drawn = tile(Tile.Honor.West)
        val state = table(waiter = player(Wind.SOUTH, Hand(tiles = waitingTiles, lastDrawn = drawn)), currentPlayerIndex = 1)
        val self = state.players[1]

        assertTrue(TAIWAN_DECLARE_READY_ACTION in validator.getLegalActions(LegalActionContext.ownTurn(state, self.id)))
        assertEquals(setOf(drawn.id, waitingTiles.last().id), validator.tileSelectionRequirement(state, self, TAIWAN_DECLARE_READY_ACTION)?.eligibleTileIds)
    }

    /** 已過地聽時機時，只有開啟報聽才提供宣告聽牌。 */
    @Test
    fun `declare ready after the opening turn requires the declared ready option`() {
        val drawn = tile(Tile.Honor.West)
        val waiter = player(Wind.SOUTH, Hand(tiles = waitingTiles, lastDrawn = drawn))
            .copy(discardPile = TaiwanDiscardPile().discardTile(tile(Tile.Honor.North)))
        val state = table(waiter = waiter, currentPlayerIndex = 1)
        val context = LegalActionContext.ownTurn(state, state.players[1].id)
        val generalReadyValidator = TaiwanLegalActionValidator(TaiwanRuleConfig(generalReadyDeclaration = true))

        assertFalse(TAIWAN_DECLARE_READY_ACTION in validator.getLegalActions(context))
        assertTrue(TAIWAN_DECLARE_READY_ACTION in generalReadyValidator.getLegalActions(context))
    }

    /**
     * 讓指定座位的玩家打出 [discarded]，取得 [TableState.players] 第二位玩家的反應。
     *
     * @param state 判定時的桌況。
     * @param discarded 打出的牌。
     * @param discarderIndex 打牌玩家的座位。
     * @return 第二位玩家的合法動作。
     */
    private fun actionsOnDiscard(state: TableState, discarded: IdentifiedTile, discarderIndex: Int = 0): List<GameAction> = validator.getLegalActions(LegalActionContext.discardReaction(state, state.players[1].id, state.players[discarderIndex].id, discarded))

    /**
     * 建立四人台灣麻將桌況，第二位是受測玩家。
     *
     * @param waiter 受測玩家。
     * @param currentPlayerIndex 目前行動的座位。
     * @return 桌況。
     */
    private fun table(waiter: MahjongPlayer, currentPlayerIndex: Int = 0): TableState {
        val players = listOf(player(Wind.EAST), waiter, player(Wind.WEST), player(Wind.NORTH))
        return FakeTableStateFactory.create(
            players = players,
            config = TaiwanRuleConfig(),
            tileWall = TileWall(List(DEFAULT_LIVE_TILES) { tile(Tile.Suit.Dot, 9) }),
            currentPlayerIndex = currentPlayerIndex,
        )
    }

    /**
     * 替換指定座位的玩家。
     *
     * @param index 座位。
     * @param transform 替換方式。
     * @return 替換後的桌況。
     */
    private fun TableState.withPlayer(index: Int, transform: (MahjongPlayer) -> MahjongPlayer): TableState = copy(players = players.mapIndexed { i, player -> if (i == index) transform(player) else player })

    /**
     * 建立指定座位的玩家。
     *
     * @param wind 座位。
     * @param hand 手牌。
     * @return 玩家。
     */
    private fun player(wind: Wind, hand: Hand = Hand()): MahjongPlayer = FakeMahjongPlayerFactory.create(wind, hand = hand, discardPile = TaiwanDiscardPile())

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
        const val DEFAULT_LIVE_TILES = 20
    }
}
