package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealCheckpoint
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealContext
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealDecision
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [RiichiWallRevealPolicy] 的純邏輯測試。 */
class RiichiWallRevealPolicyTest {
    /** 暗槓完成補牌後應立即公開對應的新指示牌。 */
    @Test
    fun `test closed kan reveals indicator immediately`() {
        val fixture = fixture(GameAction.KanType.CLOSED_KAN)

        val result = assertIs<WallRevealDecision.Updated>(
            RiichiWallRevealPolicy.resolve(fixture.context(WallRevealCheckpoint.AFTER_SUPPLEMENTAL_DRAW)),
        )
        val updated = assertIs<RiichiDynamicState>(result.dynamicRuleState)

        assertEquals(1, updated.revealedKanDoraCount)
        assertTrue(updated.pendingKanDoraReveals.isEmpty())
        assertEquals(setOf(fixture.originalDeadWall[6].id), result.newlyRevealedTileIds)
    }

    /** 大明槓與加槓完成補牌後應建立等待項目，不提前公開。 */
    @Test
    fun `test open and added kan defer indicator reveal`() {
        listOf(GameAction.KanType.OPEN_KAN, GameAction.KanType.ADDED_KAN).forEach { kanType ->
            val fixture = fixture(kanType)

            val result = assertIs<WallRevealDecision.Updated>(
                RiichiWallRevealPolicy.resolve(fixture.context(WallRevealCheckpoint.AFTER_SUPPLEMENTAL_DRAW)),
            )
            val updated = assertIs<RiichiDynamicState>(result.dynamicRuleState)

            assertEquals(0, updated.revealedKanDoraCount)
            assertEquals(listOf(RiichiPendingKanDoraReveal(fixture.actorId, kanType, 1)), updated.pendingKanDoraReveals)
            assertTrue(result.newlyRevealedTileIds.isEmpty())
        }
    }

    /** 捨牌反應無人胡牌後應公開所有等待項目。 */
    @Test
    fun `test completed discard reactions reveal pending indicator`() {
        val fixture = fixture(GameAction.KanType.OPEN_KAN)
        val pendingState = fixture.state.copy(
            dynamicRuleState = fixture.dynamicState.copy(
                pendingKanDoraReveals = listOf(
                    RiichiPendingKanDoraReveal(fixture.actorId, GameAction.KanType.OPEN_KAN, 1),
                ),
            ),
        )

        val result = assertIs<WallRevealDecision.Updated>(
            RiichiWallRevealPolicy.resolve(
                WallRevealContext(WallRevealCheckpoint.AFTER_DISCARD_REACTIONS, pendingState),
            ),
        )
        val updated = assertIs<RiichiDynamicState>(result.dynamicRuleState)

        assertEquals(1, updated.revealedKanDoraCount)
        assertTrue(updated.pendingKanDoraReveals.isEmpty())
        assertEquals(setOf(fixture.originalDeadWall[6].id), result.newlyRevealedTileIds)
    }

    /** 胡牌成立時應取消等待項目且不公開牌張。 */
    @Test
    fun `test confirmed win cancels pending indicator`() {
        val fixture = fixture(GameAction.KanType.ADDED_KAN)
        val pendingState = fixture.state.copy(
            dynamicRuleState = fixture.dynamicState.copy(
                pendingKanDoraReveals = listOf(
                    RiichiPendingKanDoraReveal(fixture.actorId, GameAction.KanType.ADDED_KAN, 1),
                ),
            ),
        )

        val result = assertIs<WallRevealDecision.Updated>(
            RiichiWallRevealPolicy.resolve(WallRevealContext(WallRevealCheckpoint.WIN_CONFIRMED, pendingState)),
        )
        val updated = assertIs<RiichiDynamicState>(result.dynamicRuleState)

        assertEquals(0, updated.revealedKanDoraCount)
        assertTrue(updated.pendingKanDoraReveals.isEmpty())
        assertTrue(result.newlyRevealedTileIds.isEmpty())
    }

    /** 再次補牌前應先公開先前等待的項目。 */
    @Test
    fun `test next supplemental draw reveals earlier pending indicator`() {
        val fixture = fixture(GameAction.KanType.OPEN_KAN)
        val pendingState = fixture.state.copy(
            dynamicRuleState = fixture.dynamicState.copy(
                pendingKanDoraReveals = listOf(
                    RiichiPendingKanDoraReveal(fixture.actorId, GameAction.KanType.OPEN_KAN, 1),
                ),
            ),
        )

        val result = assertIs<WallRevealDecision.Updated>(
            RiichiWallRevealPolicy.resolve(
                WallRevealContext(WallRevealCheckpoint.BEFORE_SUPPLEMENTAL_DRAW, pendingState),
            ),
        )

        assertEquals(1, (result.dynamicRuleState as RiichiDynamicState).revealedKanDoraCount)
    }

    /** 等待項目不得保存暗槓，因為暗槓必須立即公開。 */
    @Test
    fun `test invalid pending closed kan is rejected`() {
        val fixture = fixture(GameAction.KanType.CLOSED_KAN)
        val invalidState = fixture.state.copy(
            dynamicRuleState = fixture.dynamicState.copy(
                pendingKanDoraReveals = listOf(
                    RiichiPendingKanDoraReveal(fixture.actorId, GameAction.KanType.CLOSED_KAN, 1),
                ),
            ),
        )

        assertEquals(
            WallRevealDecision.Rejected(RiichiWallRevealPolicy.INVALID_STATE_REASON_ID),
            RiichiWallRevealPolicy.resolve(WallRevealContext(WallRevealCheckpoint.WIN_CONFIRMED, invalidState)),
        )
    }

    /** 等待項目的來源玩家必須仍在權威桌況中。 */
    @Test
    fun `test pending reveal from unknown actor is rejected`() {
        val fixture = fixture(GameAction.KanType.OPEN_KAN)
        val invalidState = fixture.state.copy(
            dynamicRuleState = fixture.dynamicState.copy(
                pendingKanDoraReveals = listOf(
                    RiichiPendingKanDoraReveal(Uuid.random(), GameAction.KanType.OPEN_KAN, 1),
                ),
            ),
        )

        assertEquals(
            WallRevealDecision.Rejected(RiichiWallRevealPolicy.INVALID_STATE_REASON_ID),
            RiichiWallRevealPolicy.resolve(WallRevealContext(WallRevealCheckpoint.WIN_CONFIRMED, invalidState)),
        )
    }

    /** 應公開牌不存在時不得只更新計數而留下部分狀態。 */
    @Test
    fun `test missing indicator tile is rejected atomically`() {
        val fixture = fixture(GameAction.KanType.CLOSED_KAN)
        val incompleteState = fixture.state.copy(initialDeadWall = fixture.state.reservedWallTiles.take(5))

        assertEquals(
            WallRevealDecision.Rejected(RiichiWallRevealPolicy.INVALID_STATE_REASON_ID),
            RiichiWallRevealPolicy.resolve(
                WallRevealContext(
                    WallRevealCheckpoint.AFTER_SUPPLEMENTAL_DRAW,
                    incompleteState,
                    fixture.actorId,
                    fixture.action,
                ),
            ),
        )
    }

    /** 建立一個已完成第一次補牌、但尚未公開追加指示牌的桌況。 */
    private fun fixture(kanType: GameAction.KanType): Fixture {
        val originalDeadWall = List(14) { index ->
            FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, (index % 9) + 1))
        }
        val replacement = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val dynamicState = RiichiDynamicState(
            completedSupplementalDrawCount = 1,
            revealedKanDoraCount = 0,
        )
        val state = FakeTableStateFactory.create(
            config = RiichiRuleConfig(),
            initialDeadWall = originalDeadWall.drop(1) + replacement,
            dynamicRuleState = dynamicState,
        )
        val actorId = state.currentPlayer.id
        val action = GameAction.Kan(kanType, Uuid.random(), emptyList())
        return Fixture(originalDeadWall, dynamicState, state, actorId, action)
    }

    /** Policy 測試共用資料。 */
    private data class Fixture(
        /** 補牌前的原始死牌區。 */
        val originalDeadWall: List<IdentifiedTile>,
        /** 已完成補牌但尚未公開的日麻動態狀態。 */
        val dynamicState: RiichiDynamicState,
        /** 對應的權威桌況。 */
        val state: TableState,
        /** 動作玩家。 */
        val actorId: Uuid,
        /** 來源槓牌動作。 */
        val action: GameAction.Kan,
    ) {
        /** 建立指定節點的 policy context。 */
        fun context(checkpoint: WallRevealCheckpoint): WallRevealContext = WallRevealContext(checkpoint, state, actorId, action)
    }
}
