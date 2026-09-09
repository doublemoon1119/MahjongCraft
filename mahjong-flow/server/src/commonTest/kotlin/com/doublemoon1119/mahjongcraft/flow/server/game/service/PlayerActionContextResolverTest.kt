package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.PendingKanReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** [PlayerActionContextResolver] 的單元測試。 */
class PlayerActionContextResolverTest {
    /** 共用的無狀態操作情境解析器。 */
    private val resolver = PlayerActionContextResolver()

    /** 驗證搶槓視窗優先於同時殘留的捨牌反應與自己回合狀態。 */
    @Test
    fun `test kan reaction has highest priority`() {
        val playerId = Uuid.random()
        val robbedTile = IdentifiedTile(Uuid.random(), Tile.Honor.White)
        val state = FakeTableStateFactory.create(
            players = listOf(
                FakeMahjongPlayerFactory.create(
                    id = playerId,
                    hand = Hand(lastDrawn = IdentifiedTile(Uuid.random(), Tile.Honor.East)),
                ),
            ),
            pendingKanReaction = PendingKanReaction(
                declarerId = Uuid.random(),
                kanAction = GameAction.Kan(GameAction.KanType.ADDED_KAN, robbedTile.id, emptyList()),
                robbedTile = robbedTile,
                eligiblePlayerIds = setOf(playerId),
            ),
            pendingReaction = PendingReaction(Uuid.random(), Uuid.random(), setOf(playerId)),
        )

        assertIs<PlayerActionContext.KanReaction>(resolver.resolveFor(state, playerId))
    }

    /** 驗證反應情境只包含尚未回應的合資格玩家。 */
    @Test
    fun `test discard reaction includes only unanswered eligible players`() {
        val answeredId = Uuid.random()
        val waitingId = Uuid.random()
        val bystanderId = Uuid.random()
        val pending = PendingReaction(
            discarderId = Uuid.random(),
            tileId = Uuid.random(),
            eligiblePlayerIds = linkedSetOf(answeredId, waitingId),
            responses = mapOf(answeredId to GameAction.Pass),
        )
        val state = FakeTableStateFactory.create(
            players = listOf(answeredId, waitingId, bystanderId).map { FakeMahjongPlayerFactory.create(id = it) },
            pendingReaction = pending,
        )

        assertEquals(listOf(waitingId), resolver.resolve(state).keys.toList())
        assertNull(resolver.resolveFor(state, answeredId))
        assertNull(resolver.resolveFor(state, bystanderId))
    }

    /** 驗證摸牌後的目前玩家取得自己回合操作情境。 */
    @Test
    fun `test drawn current player resolves own turn`() {
        val playerId = Uuid.random()
        val state = FakeTableStateFactory.create(
            players = listOf(
                FakeMahjongPlayerFactory.create(
                    id = playerId,
                    hand = Hand(lastDrawn = IdentifiedTile(Uuid.random(), Tile.Honor.East)),
                ),
            ),
        )

        assertIs<PlayerActionContext.OwnTurn>(resolver.resolveFor(state, playerId))
    }

    /** 驗證剛完成吃碰且尚未摸牌的目前玩家仍取得自己回合操作情境。 */
    @Test
    fun `test claimed meld current player resolves own turn`() {
        val playerId = Uuid.random()
        val player = FakeMahjongPlayerFactory.create(id = playerId)
            .recordAction(GameAction.Pon(Uuid.random(), emptyList()))
        val state = FakeTableStateFactory.create(players = listOf(player))

        assertIs<PlayerActionContext.OwnTurn>(resolver.resolveFor(state, playerId))
    }

    /** 驗證尚未摸牌且未剛完成吃碰時沒有玩家決策情境。 */
    @Test
    fun `test current player before mechanical draw has no context`() {
        val state = FakeTableStateFactory.create(
            players = listOf(FakeMahjongPlayerFactory.create(id = Uuid.random())),
        )

        assertEquals(emptyMap(), resolver.resolve(state))
    }
}
