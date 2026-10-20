package com.doublemoon1119.mahjongcraft.logic.judgment

import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.uuid.Uuid

/** 驗證判定資料使用同一份桌況，且來源牌只在指定的手牌形式出現一次。 */
class LegalActionContextTest {
    /** 摸牌與原始玩家不被改寫，赤牌保留完整牌種及身分。 */
    @Test
    fun `own turn derives both hands without changing the authoritative player`() {
        val drawn = identified(RiichiTileTypes.redFive(Tile.Suit.Dot))
        val table = table(Hand(tiles = listOf(identified(Tile.Honor.East)), lastDrawn = drawn))
        val context = LegalActionContext.ownTurn(table, table.currentPlayer.id)

        assertSame(table, context.tableState)
        assertSame(table.currentPlayer, context.player)
        assertSame(drawn, context.incomingTile)
        assertSame(table.currentPlayer.hand, context.handWithIncoming)
        assertNull(context.handBeforeIncoming.lastDrawn)
        assertEquals(listOf(drawn.id), context.handWithIncoming.standingTiles.filter { it.id == drawn.id }.map { it.id })
        assertEquals(RelativeDirection.Self, context.sourceDirection)
        assertEquals(table.currentPlayer.id, context.sourcePlayerId)
        assertEquals(GameAction.Draw, context.sourceAction)
        assertSame(drawn, table.currentPlayer.hand.lastDrawn)
    }

    /** 未摸牌仍可明確表示自己回合，不虛構來源牌。 */
    @Test
    fun `own turn without a draw has no incoming tile`() {
        val table = table(Hand(tiles = listOf(identified(Tile.Honor.East))))
        val context = LegalActionContext.ownTurn(table, table.currentPlayer.id)
        assertNull(context.incomingTile)
        assertEquals(context.handBeforeIncoming, context.handWithIncoming)
    }

    /** 視窗建立前即可判定捨牌，來源方向由玩家身分推導。 */
    @Test
    fun `discard reaction derives its source without requiring a pending window`() {
        val table = table(Hand())
        val source = table.players[1]
        val discarded = identified(Tile.Honor.Red)
        val context = LegalActionContext.discardReaction(table, table.currentPlayer.id, source.id, discarded)

        assertNull(table.pendingReaction)
        assertEquals(GameAction.Discard(discarded.id), context.sourceAction)
        assertEquals(table.relativeDirectionOf(table.currentPlayer.id, source.id), context.sourceDirection)
        assertEquals(source.id, context.sourcePlayerId)
        assertSame(discarded, context.incomingTile)
        assertSame(table.currentPlayer.hand, context.handBeforeIncoming)
        assertEquals(listOf(discarded), context.handWithIncoming.standingTiles)
        assertEquals(emptyList(), table.currentPlayer.hand.standingTiles)
    }

    /** 槓與第三方宣告保留完整動作，不把所有搶和來源視為加槓。 */
    @Test
    fun `declaration reactions preserve kan and extension source actions`() {
        val table = table(Hand())
        val tile = identified(Tile.Honor.North)
        val actions = listOf(
            GameAction.Kan(GameAction.KanType.ADDED_KAN, tile.id, emptyList()),
            GameAction.Extension(TestDeclaration),
        )
        for (action in actions) {
            val context = LegalActionContext.declarationReaction(
                tableState = table,
                playerId = table.currentPlayer.id,
                declarerId = table.players[1].id,
                declaredAction = action,
                declaredTile = tile,
            )
            assertNull(table.pendingRobbingReaction)
            assertSame(action, context.sourceAction)
            assertSame(tile, context.handWithIncoming.lastDrawn)
        }
    }

    /** 同牌面但不同實體不是重複牌，不以牌面大小去重。 */
    @Test
    fun `different physical copies of the same face remain distinct`() {
        val owned = identified(Tile.Honor.North)
        val source = identified(Tile.Honor.North)
        val table = table(Hand(tiles = listOf(owned)))
        val context = LegalActionContext.discardReaction(table, table.currentPlayer.id, table.players[1].id, source)
        assertEquals(listOf(owned, source), context.handWithIncoming.standingTiles)
    }

    /** 原始手牌重複持有來源牌身分時拒絕，不偷偷刪除牌張。 */
    @Test
    fun `duplicate incoming identity is rejected for own turn and reactions`() {
        val tile = identified(Tile.Honor.North)
        val own = table(Hand(tiles = listOf(tile), lastDrawn = tile))
        assertFailsWith<IllegalArgumentException> { LegalActionContext.ownTurn(own, own.currentPlayer.id) }
        val reaction = table(Hand(tiles = listOf(tile)))
        assertFailsWith<IllegalArgumentException> {
            LegalActionContext.discardReaction(reaction, reaction.currentPlayer.id, reaction.players[1].id, tile)
        }
    }

    /** 玩家、來源玩家或來源形式不一致時在建立資料時明確拒絕。 */
    @Test
    fun `invalid player and ambiguous reaction inputs are rejected`() {
        val table = table(Hand())
        val tile = identified(Tile.Honor.White)
        assertFailsWith<IllegalArgumentException> { LegalActionContext.ownTurn(table, Uuid.random()) }
        assertFailsWith<IllegalArgumentException> {
            LegalActionContext.discardReaction(table, table.currentPlayer.id, Uuid.random(), tile)
        }
        assertFailsWith<IllegalArgumentException> {
            LegalActionContext.discardReaction(table, table.currentPlayer.id, table.currentPlayer.id, tile)
        }
        val withDraw = table(Hand(lastDrawn = tile))
        assertFailsWith<IllegalArgumentException> {
            LegalActionContext.discardReaction(withDraw, withDraw.currentPlayer.id, withDraw.players[1].id, identified(Tile.Honor.Red))
        }
    }

    /**
     * 建立四人桌況，第一位玩家使用指定手牌。
     *
     * @param hand 第一位玩家的原始手牌。
     * @return 沒有開啟反應視窗的測試桌況。
     */
    private fun table(hand: Hand): TableState {
        val players = Wind.entries.mapIndexed { index, wind ->
            MahjongPlayer(
                id = Uuid.random(),
                initialSeatIndex = index,
                hand = if (index == 0) hand else Hand(),
                discardPile = RiichiDiscardPile(),
                seatWind = wind,
            )
        }
        return TableState(
            id = Uuid.random(),
            players = players,
            config = RiichiRuleConfig(),
            tileWall = TileWall(),
            dealerPlayerId = players.first().id,
        )
    }

    /**
     * 建立具有獨立身分的測試牌。
     *
     * @param tile 牌面。
     * @return 測試牌張。
     */
    private fun identified(tile: Tile): IdentifiedTile = IdentifiedTile(Uuid.random(), tile)

    /** 可被搶和的第三方宣告範例。 */
    private data object TestDeclaration : ExtensionGameAction {
        override val id: String = "test:declare"
    }
}
