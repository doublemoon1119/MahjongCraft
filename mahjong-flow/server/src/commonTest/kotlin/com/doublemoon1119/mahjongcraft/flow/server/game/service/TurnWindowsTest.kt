package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證摸牌與自己回合決策的時機判斷，涵蓋輪到誰、是否已摸牌、是否剛鳴牌與各種反應等待。 */
class TurnWindowsTest {
    private val currentId = Uuid.random()
    private val otherId = Uuid.random()
    private val drawn = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 1))

    /** 輪到自己、還沒摸牌且沒有人在等待回應時才能摸牌；同一時間不處於自己回合的決策。 */
    @Test
    fun `draw window opens only at the start of the current player's turn`() {
        val state = table()

        assertTrue(state.isDrawWindowOpenFor(currentId))
        assertFalse(state.isDrawWindowOpenFor(otherId))
        assertFalse(state.isOwnTurnWindowOpenFor(currentId))
    }

    /** 摸牌後或剛碰完時處於自己回合的決策，且不能再摸牌。 */
    @Test
    fun `drawn tile or a fresh call opens the own turn window and closes the draw window`() {
        listOf(table(hand = Hand(lastDrawn = drawn)), table(justCalled = true)).forEach { state ->
            assertTrue(state.isOwnTurnWindowOpenFor(currentId))
            assertFalse(state.isOwnTurnWindowOpenFor(otherId))
            assertFalse(state.isDrawWindowOpenFor(currentId))
        }
    }

    /** 有人正在決定是否回應捨牌或搶和時，任何人都不能摸牌，也不處於自己回合的決策。 */
    @Test
    fun `pending reactions close both windows`() {
        val pendingDiscard = PendingReaction(discarderId = currentId, tileId = drawn.id, eligiblePlayerIds = setOf(otherId), ronPlayerIds = setOf(otherId), id = Uuid.random())
        val pendingRobbing = PendingRobbingReaction(
            declarerId = currentId,
            declaredAction = GameAction.Kan(GameAction.KanType.ADDED_KAN, drawn.id, emptyList()),
            robbedTile = drawn,
            eligiblePlayerIds = setOf(otherId),
        )
        listOf(
            table().copy(pendingReaction = pendingDiscard),
            table(hand = Hand(lastDrawn = drawn)).copy(pendingRobbingReaction = pendingRobbing),
        ).forEach { state ->
            assertTrue(state.hasPendingReaction)
            assertFalse(state.isDrawWindowOpenFor(currentId))
            assertFalse(state.isOwnTurnWindowOpenFor(currentId))
        }
    }

    /**
     * 建立由 [currentId] 坐莊、輪到他的兩人桌況。
     *
     * @param hand 目前玩家的手牌。
     * @param justCalled 目前玩家最後一個動作是否為碰。
     * @return 測試用桌況。
     */
    private fun table(hand: Hand = Hand(), justCalled: Boolean = false): TableState {
        val current = FakeMahjongPlayerFactory.create(id = currentId, initialSeat = Wind.EAST, hand = hand)
            .let { if (justCalled) it.recordAction(GameAction.Pon(drawn.id, emptyList())) else it }
        val other = FakeMahjongPlayerFactory.create(id = otherId, initialSeat = Wind.SOUTH)
        return FakeTableStateFactory.create(players = listOf(current, other), currentPlayerIndex = 0)
    }
}
