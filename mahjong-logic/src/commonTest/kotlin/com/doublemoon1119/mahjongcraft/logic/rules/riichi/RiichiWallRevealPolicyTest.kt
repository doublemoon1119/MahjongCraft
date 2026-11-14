package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealCheckpoint
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealContext
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealDecision
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiTableComponents
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
            value = RiichiWallRevealPolicy.resolve(fixture.context(WallRevealCheckpoint.AFTER_SUPPLEMENTAL_DRAW)),
        )
        val updated = requireNotNull(result.tableComponents.values[RiichiTableComponents.WALL_PROGRESS])

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
                value = RiichiWallRevealPolicy.resolve(fixture.context(WallRevealCheckpoint.AFTER_SUPPLEMENTAL_DRAW)),
            )
            val updated = requireNotNull(result.tableComponents.values[RiichiTableComponents.WALL_PROGRESS])

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
            components = fixture.state.components.with(
                type = RiichiTableComponents.WALL_PROGRESS,
                value = fixture.progress.copy(
                    pendingKanDoraReveals = listOf(
                        element = RiichiPendingKanDoraReveal(fixture.actorId, GameAction.KanType.OPEN_KAN, 1),
                    ),
                ),
            ),
        )

        val result = assertIs<WallRevealDecision.Updated>(
            value = RiichiWallRevealPolicy.resolve(
                context = WallRevealContext(WallRevealCheckpoint.AFTER_DISCARD_REACTIONS, pendingState),
            ),
        )
        val updated = requireNotNull(result.tableComponents.values[RiichiTableComponents.WALL_PROGRESS])

        assertEquals(1, updated.revealedKanDoraCount)
        assertTrue(updated.pendingKanDoraReveals.isEmpty())
        assertEquals(setOf(fixture.originalDeadWall[6].id), result.newlyRevealedTileIds)
    }

    /** 胡牌成立時應取消等待項目且不公開牌張。 */
    @Test
    fun `test confirmed win cancels pending indicator`() {
        val fixture = fixture(GameAction.KanType.ADDED_KAN)
        val pendingState = fixture.state.copy(
            components = fixture.state.components.with(
                type = RiichiTableComponents.WALL_PROGRESS,
                value = fixture.progress.copy(
                    pendingKanDoraReveals = listOf(
                        element = RiichiPendingKanDoraReveal(fixture.actorId, GameAction.KanType.ADDED_KAN, 1),
                    ),
                ),
            ),
        )

        val result = assertIs<WallRevealDecision.Updated>(
            value = RiichiWallRevealPolicy.resolve(WallRevealContext(WallRevealCheckpoint.WIN_CONFIRMED, pendingState)),
        )
        val updated = requireNotNull(result.tableComponents.values[RiichiTableComponents.WALL_PROGRESS])

        assertEquals(0, updated.revealedKanDoraCount)
        assertTrue(updated.pendingKanDoraReveals.isEmpty())
        assertTrue(result.newlyRevealedTileIds.isEmpty())
    }

    /** 再次補牌前應先公開先前等待的項目。 */
    @Test
    fun `test next supplemental draw reveals earlier pending indicator`() {
        val fixture = fixture(GameAction.KanType.OPEN_KAN)
        val pendingState = fixture.state.copy(
            components = fixture.state.components.with(
                type = RiichiTableComponents.WALL_PROGRESS,
                value = fixture.progress.copy(
                    pendingKanDoraReveals = listOf(
                        element = RiichiPendingKanDoraReveal(fixture.actorId, GameAction.KanType.OPEN_KAN, 1),
                    ),
                ),
            ),
        )

        val result = assertIs<WallRevealDecision.Updated>(
            value = RiichiWallRevealPolicy.resolve(
                context = WallRevealContext(WallRevealCheckpoint.BEFORE_SUPPLEMENTAL_DRAW, pendingState),
            ),
        )

        assertEquals(1, requireNotNull(result.tableComponents.values[RiichiTableComponents.WALL_PROGRESS]).revealedKanDoraCount)
    }

    /** 等待項目不得保存暗槓，因為暗槓必須立即公開。 */
    @Test
    fun `test invalid pending closed kan is rejected`() {
        val fixture = fixture(GameAction.KanType.CLOSED_KAN)
        val invalidState = fixture.state.copy(
            components = fixture.state.components.with(
                type = RiichiTableComponents.WALL_PROGRESS,
                value = fixture.progress.copy(
                    pendingKanDoraReveals = listOf(
                        element = RiichiPendingKanDoraReveal(fixture.actorId, GameAction.KanType.CLOSED_KAN, 1),
                    ),
                ),
            ),
        )

        assertEquals(
            expected = WallRevealDecision.Rejected(RiichiWallRevealPolicy.INVALID_STATE_REASON_ID),
            actual = RiichiWallRevealPolicy.resolve(WallRevealContext(WallRevealCheckpoint.WIN_CONFIRMED, invalidState)),
        )
    }

    /** 等待項目的來源玩家必須仍在權威桌況中。 */
    @Test
    fun `test pending reveal from unknown actor is rejected`() {
        val fixture = fixture(GameAction.KanType.OPEN_KAN)
        val invalidState = fixture.state.copy(
            components = fixture.state.components.with(
                type = RiichiTableComponents.WALL_PROGRESS,
                value = fixture.progress.copy(
                    pendingKanDoraReveals = listOf(
                        element = RiichiPendingKanDoraReveal(Uuid.random(), GameAction.KanType.OPEN_KAN, 1),
                    ),
                ),
            ),
        )

        assertEquals(
            expected = WallRevealDecision.Rejected(RiichiWallRevealPolicy.INVALID_STATE_REASON_ID),
            actual = RiichiWallRevealPolicy.resolve(WallRevealContext(WallRevealCheckpoint.WIN_CONFIRMED, invalidState)),
        )
    }

    /** 應公開牌不存在時不得只更新計數而留下部分狀態。 */
    @Test
    fun `test missing indicator tile is rejected atomically`() {
        val fixture = fixture(GameAction.KanType.CLOSED_KAN)
        val incompleteState = fixture.state.copy(reservedWallTiles = fixture.state.reservedWallTiles.take(5))

        assertEquals(
            expected = WallRevealDecision.Rejected(RiichiWallRevealPolicy.INVALID_STATE_REASON_ID),
            actual = RiichiWallRevealPolicy.resolve(
                context = WallRevealContext(
                    checkpoint = WallRevealCheckpoint.AFTER_SUPPLEMENTAL_DRAW,
                    tableState = incompleteState,
                    actorPlayerId = fixture.actorId,
                    sourceAction = fixture.action,
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
        val progress = RiichiWallProgress(completedSupplementalDrawCount = 1, revealedKanDoraCount = 0)
        val state = FakeTableStateFactory.create(
            config = RiichiRuleConfig(),
            reservedWallTiles = originalDeadWall.drop(1) + replacement,
            components = riichiTableComponents(wallProgress = progress),
        )
        val actorId = state.currentPlayer.id
        val action = GameAction.Kan(kanType, Uuid.random(), emptyList())
        return Fixture(originalDeadWall, progress, state, actorId, action)
    }

    /**
     * 公開政策測試資料。
     *
     * @property originalDeadWall 補牌前的死牌區。
     * @property progress 已完成補牌、尚未公開的進度。
     * @property state 對應的權威桌況。
     * @property actorId 動作玩家。
     * @property action 來源槓牌動作。
     */
    private data class Fixture(
        val originalDeadWall: List<IdentifiedTile>,
        val progress: RiichiWallProgress,
        val state: TableState,
        val actorId: Uuid,
        val action: GameAction.Kan,
    ) {
        /** 建立指定節點的 policy context。 */
        fun context(checkpoint: WallRevealCheckpoint): WallRevealContext = WallRevealContext(checkpoint, state, actorId, action)
    }
}
