package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.AutomaticControlContext
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** [RiichiAutomaticControlPolicy] 的優先順序與安全條件測試。 */
class RiichiAutomaticControlPolicyTest {
    /** 自動和牌優先於其他自動控制。 */
    @Test
    fun `test auto win selects winning action first`() {
        val context = context(
            legalActions = listOf(GameAction.Pon(Uuid.random(), emptyList()), GameAction.Ron(Uuid.random()), GameAction.Pass),
            enabledIds = setOf(BuiltInAutomaticControlIds.AUTO_WIN, BuiltInAutomaticControlIds.DECLINE_CALLS),
        )

        val result = RiichiAutomaticControlPolicy.evaluate(context)

        assertEquals(context.legalActions[1], result.immediateAction?.action)
    }

    /** 不鳴牌隱藏吃碰與大明槓，但保留和牌、過牌及其他槓。 */
    @Test
    fun `test decline calls only hides optional calls`() {
        val incomingId = Uuid.random()
        val actions = listOf(
            GameAction.Chi(incomingId, emptyList()),
            GameAction.Pon(incomingId, emptyList()),
            GameAction.Kan(GameAction.KanType.OPEN_KAN, incomingId, emptyList()),
            GameAction.Kan(GameAction.KanType.CLOSED_KAN, incomingId, emptyList()),
            GameAction.Ron(incomingId),
            GameAction.Pass,
        )

        val result = RiichiAutomaticControlPolicy.evaluate(
            context(actions, setOf(BuiltInAutomaticControlIds.DECLINE_CALLS)),
        )

        assertEquals(actions.take(3).toSet(), result.hiddenActions)
        assertNull(result.immediateAction)
    }

    /** 隱藏所有鳴牌後只剩過牌時立即過牌。 */
    @Test
    fun `test decline calls immediately passes when no other choice remains`() {
        val incomingId = Uuid.random()
        val result = RiichiAutomaticControlPolicy.evaluate(
            context(
                listOf(GameAction.Pon(incomingId, emptyList()), GameAction.Pass),
                setOf(BuiltInAutomaticControlIds.DECLINE_CALLS),
            ),
        )

        assertEquals(GameAction.Pass, result.immediateAction?.action)
    }

    /** 自動摸切只會捨出剛摸入的牌，且有其他特殊選擇時不搶先執行。 */
    @Test
    fun `test auto tsumogiri requires no remaining special choice`() {
        val drawn = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val enabled = setOf(BuiltInAutomaticControlIds.AUTO_TSUMOGIRI)

        val immediate = RiichiAutomaticControlPolicy.evaluate(context(emptyList(), enabled, drawn.id))
        val blocked = RiichiAutomaticControlPolicy.evaluate(context(listOf(GameAction.Tsumo), enabled, drawn.id))

        assertEquals(GameAction.Discard(drawn.id), immediate.immediateAction?.action)
        assertNull(blocked.immediateAction)
    }

    /** 建立只供 policy 判斷的最小權威情境。 */
    private fun context(
        legalActions: List<GameAction>,
        enabledIds: Set<String>,
        drawnTileId: Uuid? = null,
    ): AutomaticControlContext {
        val drawn = drawnTileId?.let { FakeIdentifiedTileFactory.create(Tile.Honor.East, it) }
        val player = FakeMahjongPlayerFactory.create(
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = drawn),
        )
        val state = FakeTableStateFactory.create(players = listOf(player), currentPlayerIndex = 0)
        return AutomaticControlContext(state, state.currentPlayer, legalActions, enabledIds)
    }
}
