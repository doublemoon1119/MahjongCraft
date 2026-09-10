package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawContext
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.UnsupportedSupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [RiichiSupplementalDrawPolicy] 的純邏輯測試。 */
class RiichiSupplementalDrawPolicyTest {
    /** 驗證首次構牌使用第一個嶺上槽位，並以活牌尾端補回死牌區。 */
    @Test
    fun `test first supplemental draw updates wall and dead wall atomically`() {
        val deadWall = List(14) { index ->
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (index % 9) + 1))
        }
        val liveWall = List(3) { index ->
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Bamboo, index + 1))
        }
        val state = FakeTableStateFactory.create(
            config = RiichiRuleConfig(),
            tileWall = TileWall(liveWall),
            initialDeadWall = deadWall,
            dynamicRuleState = RiichiDynamicState(),
        )

        val result = assertIs<SupplementalDrawDecision.Completed>(
            RiichiSupplementalDrawPolicy.resolve(context(state)),
        )

        assertEquals(listOf(deadWall.first()), result.drawnTiles)
        assertEquals(liveWall.dropLast(1), result.tileWall.getAllTiles())
        assertEquals(liveWall.last(), result.reservedWallTiles.first())
        assertEquals(14, result.reservedWallTiles.size)
        assertEquals(1, (result.dynamicRuleState as RiichiDynamicState).completedSupplementalDrawCount)
        assertEquals(setOf(deadWall[6].id), result.newlyRevealedTileIds)
    }

    /** 驗證補牌次數決定嶺上槽位，且第五次會由規則明確拒絕。 */
    @Test
    fun `test supplemental draw count selects slot and enforces limit`() {
        val deadWall = List(14) { FakeIdentifiedTileFactory.create(Tile.Honor.White) }
        val liveTile = FakeIdentifiedTileFactory.create(Tile.Honor.Green)
        val state = FakeTableStateFactory.create(
            config = RiichiRuleConfig(),
            tileWall = TileWall(listOf(liveTile)),
            initialDeadWall = deadWall,
            dynamicRuleState = RiichiDynamicState(completedSupplementalDrawCount = 3),
        )
        val fourth = assertIs<SupplementalDrawDecision.Completed>(
            RiichiSupplementalDrawPolicy.resolve(context(state)),
        )
        assertEquals(deadWall[3], fourth.drawnTiles.single())

        val limitState = state.copy(
            dynamicRuleState = RiichiDynamicState(completedSupplementalDrawCount = 4),
        )
        val rejected = assertIs<SupplementalDrawDecision.Rejected>(
            RiichiSupplementalDrawPolicy.resolve(context(limitState)),
        )
        assertEquals(RiichiSupplementalDrawPolicy.LIMIT_REACHED_REASON_ID, rejected.reasonId)
    }

    /** 驗證缺少活牌尾端補充牌時不回傳半完成結果。 */
    @Test
    fun `test exhausted live wall rejects supplemental draw`() {
        val deadWall = List(14) { FakeIdentifiedTileFactory.create(Tile.Honor.White) }
        val state = FakeTableStateFactory.create(
            config = RiichiRuleConfig(),
            tileWall = TileWall(),
            initialDeadWall = deadWall,
            dynamicRuleState = RiichiDynamicState(),
        )

        val result = assertIs<SupplementalDrawDecision.Rejected>(
            RiichiSupplementalDrawPolicy.resolve(context(state)),
        )

        assertEquals(SupplementalDrawReasonIds.WALL_EXHAUSTED, result.reasonId)
    }

    /** 驗證非槓動作不會被日麻補牌 policy 改動。 */
    @Test
    fun `test non kan action does not require supplemental draw`() {
        val state = FakeTableStateFactory.create(
            config = RiichiRuleConfig(),
            dynamicRuleState = RiichiDynamicState(),
        )
        val context = SupplementalDrawContext(state, state, state.currentPlayer.id, GameAction.Draw)

        assertTrue(RiichiSupplementalDrawPolicy.resolve(context) === SupplementalDrawDecision.NotRequired)
    }

    /** 驗證未提供補牌能力的規則預設會明確拒絕，而不套用日麻行為。 */
    @Test
    fun `test unsupported policy rejects without fallback`() {
        val state = FakeTableStateFactory.create(config = RiichiRuleConfig())

        val result = assertIs<SupplementalDrawDecision.Rejected>(
            UnsupportedSupplementalDrawPolicy.resolve(context(state)),
        )

        assertEquals(UnsupportedSupplementalDrawPolicy.UNSUPPORTED_REASON_ID, result.reasonId)
    }

    /** 建立以同一桌況代表動作前後的 policy 測試輸入。 */
    private fun context(state: TableState): SupplementalDrawContext = SupplementalDrawContext(
        tableStateBeforeAction = state,
        tableStateAfterAction = state,
        actorPlayerId = state.currentPlayer.id,
        action = GameAction.Kan(GameAction.KanType.CLOSED_KAN, Uuid.random(), emptyList()),
    )
}
