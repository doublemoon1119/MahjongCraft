package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證 [TableState.turnPhase] 的桌況檢查，以及摸牌工具對階段的要求與設定。 */
class TurnPhaseTest {
    /** 提供摸牌反應的日麻規則模組。 */
    private val module = RiichiRuleModule(id = "mahjongcraft:riichi", config = RiichiRuleConfig())

    /** 等摸牌與等捨牌時目前玩家手上不能有剛摸的牌；等行動時可以有。 */
    @Test
    fun `phases without a pending draw reject a drawn tile`() {
        val drawn = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val holder = FakeMahjongPlayerFactory.create(Wind.EAST).let { it.copy(hand = it.hand.copy(lastDrawn = drawn)) }
        val other = FakeMahjongPlayerFactory.create(Wind.SOUTH)

        listOf(TurnPhase.AwaitingDraw, TurnPhase.AwaitingDiscard).forEach { phase ->
            assertFailsWith<IllegalArgumentException> {
                FakeTableStateFactory.create(players = listOf(holder, other), turnPhase = phase)
            }
        }
        assertEquals(
            TurnPhase.AwaitingAction,
            FakeTableStateFactory.create(players = listOf(holder, other), turnPhase = TurnPhase.AwaitingAction).turnPhase,
        )
    }

    /** 等待回應的資料只能從對應階段讀出，其他階段讀不到。 */
    @Test
    fun `pending reactions are read from the waiting phases only`() {
        val players = listOf(FakeMahjongPlayerFactory.create(Wind.EAST), FakeMahjongPlayerFactory.create(Wind.SOUTH))
        val reaction = PendingReaction(
            discarderId = players[0].id,
            tileId = Uuid.random(),
            eligiblePlayerIds = setOf(players[1].id),
            ronPlayerIds = emptySet(),
            id = Uuid.random(),
        )
        val robbing = PendingRobbingReaction(
            declarerId = players[0].id,
            declaredAction = GameAction.Pass,
            robbedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White),
            eligiblePlayerIds = setOf(players[1].id),
        )

        val discardWaiting = FakeTableStateFactory.create(players = players, turnPhase = TurnPhase.AwaitingDiscardReaction(reaction))
        assertEquals(reaction, discardWaiting.pendingReaction)
        assertNull(discardWaiting.pendingRobbingReaction)

        val robbingWaiting = FakeTableStateFactory.create(players = players, turnPhase = TurnPhase.AwaitingRobbingReaction(robbing))
        assertEquals(robbing, robbingWaiting.pendingRobbingReaction)
        assertNull(robbingWaiting.pendingReaction)

        val drawing = FakeTableStateFactory.create(players = players, turnPhase = TurnPhase.AwaitingDraw)
        assertNull(drawing.pendingReaction)
        assertNull(drawing.pendingRobbingReaction)
    }

    /** 從活牌摸牌後，牌進摸牌位、記下摸牌，階段改為等行動。 */
    @Test
    fun `drawing from the live wall moves the turn to awaiting action`() {
        val state = FakeTableStateFactory.create(turnPhase = TurnPhase.AwaitingDraw)

        val draw = assertNotNull(state.drawFromLiveWall(state.currentPlayer.id))

        assertEquals(TurnPhase.AwaitingAction, draw.tableState.turnPhase)
        assertEquals(draw.tile, draw.tableState.currentPlayer.hand.lastDrawn)
        assertEquals(GameAction.Draw, draw.tableState.currentPlayer.actionHistory.last())
        assertEquals(state.tileWall.getAllTiles().size - 1, draw.tableState.tileWall.getAllTiles().size)
    }

    /** 不在等摸牌階段，或摸牌的不是目前玩家時，摸牌工具直接拒絕。 */
    @Test
    fun `drawing from the live wall requires the current player awaiting a draw`() {
        val players = listOf(FakeMahjongPlayerFactory.create(Wind.EAST), FakeMahjongPlayerFactory.create(Wind.SOUTH))
        val reaction = PendingReaction(
            discarderId = players[1].id,
            tileId = Uuid.random(),
            eligiblePlayerIds = setOf(players[0].id),
            ronPlayerIds = emptySet(),
            id = Uuid.random(),
        )
        listOf(
            TurnPhase.AwaitingAction,
            TurnPhase.AwaitingDiscard,
            TurnPhase.AwaitingDiscardReaction(reaction),
            TurnPhase.RoundOver(RoundConclusion.AbortiveDraw(TestExhaustiveDrawReason)),
        ).forEach { phase ->
            val state = FakeTableStateFactory.create(players = players, turnPhase = phase)
            assertFailsWith<IllegalArgumentException>("$phase") { state.drawFromLiveWall(state.currentPlayer.id) }
        }
        val awaiting = FakeTableStateFactory.create(players = players, turnPhase = TurnPhase.AwaitingDraw)
        assertFailsWith<IllegalArgumentException> { awaiting.drawFromLiveWall(players[1].id) }
    }

    /** 莊家開門摸牌只在莊家等摸牌時發生；莊家已經摸過時不再摸第二張。 */
    @Test
    fun `dealer opening draw happens once while the dealer awaits a draw`() {
        val state = FakeTableStateFactory.create(config = module.config, turnPhase = TurnPhase.AwaitingDraw)

        val draw = assertNotNull(state.drawDealerOpeningTile(module))

        assertEquals(TurnPhase.AwaitingAction, draw.tableState.turnPhase)
        assertNull(draw.tableState.drawDealerOpeningTile(module))
        assertNull(state.copy(currentPlayerIndex = 1).drawDealerOpeningTile(module))
    }

    /** 測試用的流局原因。 */
    private data object TestExhaustiveDrawReason : ExhaustiveDrawReason {
        override val id: String = "test:abortive"
    }
}
