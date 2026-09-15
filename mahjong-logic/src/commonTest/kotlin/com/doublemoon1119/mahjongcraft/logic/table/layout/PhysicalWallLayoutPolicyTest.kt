package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** [PhysicalWallLayoutPolicy] 規則中立契約與驗證測試。 */
class PhysicalWallLayoutPolicyTest {
    /** 預設 continuous policy 應直接保留牌牆拓樸格位。 */
    @Test
    fun `continuous policy creates zero-offset initial layout`() {
        val bottomTile = testTile()
        val topTile = testTile()
        val structure = mapOf(
            bottomTile.id to TileWallPosition(side = 0, stack = 3, layer = 0),
            topTile.id to TileWallPosition(side = 0, stack = 3, layer = 1),
        )
        val result = TileWallLayoutResult(
            drawOrder = listOf(topTile, bottomTile),
            initialDeadWall = emptyList(),
            structure = structure,
        )

        val decision = ContinuousPhysicalWallLayoutPolicy.createInitialLayoutValidated(
            InitialPhysicalWallLayoutContext(result, WallOpening(0, 1), result.structure.keys),
        )

        val completed = assertIs<InitialPhysicalWallLayoutDecision.Completed>(decision)
        assertEquals(
            structure.mapValues { (_, position) -> TileWallPlacement(position) },
            completed.layout.placements,
        )
    }

    /** 單一 phase 應可讓同一墩上下兩張同步移動並保持相對層位。 */
    @Test
    fun `whole stack fixture moves both layers in one phase`() {
        val bottomTile = testTile()
        val topTile = testTile()
        val wall = TileWall(listOf(topTile, bottomTile))
        val state = FakeTableStateFactory.create(tileWall = wall)
        val bottomStart = TileWallPlacement(TileWallPosition(side = 0, stack = 4, layer = 0))
        val topStart = TileWallPlacement(TileWallPosition(side = 0, stack = 4, layer = 1))
        val bottomEnd = TileWallPlacement(TileWallPosition(side = 0, stack = 5, layer = 0))
        val topEnd = TileWallPlacement(TileWallPosition(side = 0, stack = 5, layer = 1))
        val current = TileWallPhysicalLayout(mapOf(bottomTile.id to bottomStart, topTile.id to topStart))
        val policy = completedPolicy(
            layout = TileWallPhysicalLayout(mapOf(bottomTile.id to bottomEnd, topTile.id to topEnd)),
            phases = listOf(
                PhysicalWallLayoutTransitionPhase(
                    listOf(
                        PhysicalWallTileMove(bottomTile.id, bottomStart, bottomEnd),
                        PhysicalWallTileMove(topTile.id, topStart, topEnd),
                    ),
                ),
            ),
        )

        val decision = policy.resolveTransitionValidated(
            transitionContext(state, state, current),
        )

        val completed = assertIs<PhysicalWallLayoutTransitionDecision.Completed>(decision)
        assertEquals(1, completed.phases.size)
        assertEquals(2, completed.phases.single().moves.size)
        assertEquals(
            bottomEnd.position.layer - bottomStart.position.layer,
            topEnd.position.layer - topStart.position.layer,
        )
        assertEquals(
            bottomEnd.position.stack - bottomStart.position.stack,
            topEnd.position.stack - topStart.position.stack,
        )
    }

    /** 已離開牌牆的牌應由 continuous policy 移除，其他 placement 不變。 */
    @Test
    fun `continuous policy removes tile that left wall`() {
        val remainingTile = testTile()
        val drawnTile = testTile()
        val before = FakeTableStateFactory.create(tileWall = TileWall(listOf(remainingTile, drawnTile)))
        val after = FakeTableStateFactory.create(
            id = before.id,
            players = before.players,
            dealerPlayerId = before.dealerPlayerId,
            tileWall = TileWall(listOf(remainingTile)),
        )
        val remainingPlacement = TileWallPlacement(TileWallPosition(side = 0, stack = 0, layer = 0))
        val current = TileWallPhysicalLayout(
            mapOf(
                remainingTile.id to remainingPlacement,
                drawnTile.id to TileWallPlacement(TileWallPosition(side = 0, stack = 0, layer = 1)),
            ),
        )

        val decision = ContinuousPhysicalWallLayoutPolicy.resolveTransitionValidated(
            transitionContext(before, after, current),
        )

        val completed = assertIs<PhysicalWallLayoutTransitionDecision.Completed>(decision)
        assertEquals(mapOf(remainingTile.id to remainingPlacement), completed.layout.placements)
        assertEquals(emptyList(), completed.phases)
    }

    /** 初始 policy 遺漏或加入陌生 UUID 時應轉成安全拒絕。 */
    @Test
    fun `initial layout rejects mismatched tile ids`() {
        val tile = testTile()
        val position = TileWallPosition(side = 0, stack = 0, layer = 0)
        val result = TileWallLayoutResult(listOf(tile), emptyList(), mapOf(tile.id to position))
        val policy = object : PhysicalWallLayoutPolicy {
            override fun createInitialLayout(
                context: InitialPhysicalWallLayoutContext,
            ): InitialPhysicalWallLayoutDecision = InitialPhysicalWallLayoutDecision.Completed(
                TileWallPhysicalLayout(mapOf(Uuid.random() to TileWallPlacement(position))),
            )

            override fun resolveTransition(
                context: PhysicalWallLayoutTransitionContext,
            ): PhysicalWallLayoutTransitionDecision = PhysicalWallLayoutTransitionDecision.Unchanged
        }

        val decision = policy.createInitialLayoutValidated(
            InitialPhysicalWallLayoutContext(result, WallOpening(0, 1), result.structure.keys),
        )

        assertEquals(
            InitialPhysicalWallLayoutDecision.Rejected(PhysicalWallLayoutReasonIds.INVALID_RESULT),
            decision,
        )
    }

    /** 改變 placement 卻未提供對應 move 時應轉成安全拒絕。 */
    @Test
    fun `transition rejects changed placement without move`() {
        val tile = testTile()
        val state = FakeTableStateFactory.create(tileWall = TileWall(listOf(tile)))
        val current = TileWallPhysicalLayout(
            mapOf(tile.id to TileWallPlacement(TileWallPosition(side = 0, stack = 0, layer = 0))),
        )
        val changed = TileWallPhysicalLayout(
            mapOf(tile.id to TileWallPlacement(TileWallPosition(side = 0, stack = 1, layer = 0))),
        )

        val decision = completedPolicy(changed, emptyList()).resolveTransitionValidated(
            transitionContext(state, state, current),
        )

        assertEquals(
            PhysicalWallLayoutTransitionDecision.Rejected(PhysicalWallLayoutReasonIds.INVALID_RESULT),
            decision,
        )
    }

    /** Move 宣告的來源與 phase 開始布局不同時應拒絕。 */
    @Test
    fun `transition rejects stale move source`() {
        val tile = testTile()
        val state = FakeTableStateFactory.create(tileWall = TileWall(listOf(tile)))
        val start = TileWallPlacement(TileWallPosition(side = 0, stack = 0, layer = 0))
        val stale = TileWallPlacement(TileWallPosition(side = 0, stack = 1, layer = 0))
        val end = TileWallPlacement(TileWallPosition(side = 0, stack = 2, layer = 0))
        val current = TileWallPhysicalLayout(mapOf(tile.id to start))
        val completed = completedPolicy(
            TileWallPhysicalLayout(mapOf(tile.id to end)),
            listOf(PhysicalWallLayoutTransitionPhase(listOf(PhysicalWallTileMove(tile.id, stale, end)))),
        )

        val decision = completed.resolveTransitionValidated(transitionContext(state, state, current))

        assertEquals(
            PhysicalWallLayoutTransitionDecision.Rejected(PhysicalWallLayoutReasonIds.INVALID_RESULT),
            decision,
        )
    }

    /** 同 phase 的目的位置與未移動牌重疊時應拒絕。 */
    @Test
    fun `transition rejects overlapping phase destination`() {
        val moving = testTile()
        val stationary = testTile()
        val state = FakeTableStateFactory.create(tileWall = TileWall(listOf(moving, stationary)))
        val movingStart = TileWallPlacement(TileWallPosition(side = 0, stack = 0, layer = 0))
        val occupied = TileWallPlacement(TileWallPosition(side = 0, stack = 1, layer = 0))
        val current = TileWallPhysicalLayout(mapOf(moving.id to movingStart, stationary.id to occupied))
        val completed = completedPolicy(
            TileWallPhysicalLayout(mapOf(moving.id to occupied, stationary.id to movingStart)),
            listOf(
                PhysicalWallLayoutTransitionPhase(
                    listOf(PhysicalWallTileMove(moving.id, movingStart, occupied)),
                ),
                PhysicalWallLayoutTransitionPhase(
                    listOf(PhysicalWallTileMove(stationary.id, occupied, movingStart)),
                ),
            ),
        )

        val decision = completed.resolveTransitionValidated(transitionContext(state, state, current))

        assertEquals(
            PhysicalWallLayoutTransitionDecision.Rejected(PhysicalWallLayoutReasonIds.INVALID_RESULT),
            decision,
        )
    }

    /** 非 namespaced 拒絕原因與無效正規化座標應立即失敗。 */
    @Test
    fun `invalid reason id and non-finite offset fail fast`() {
        assertFailsWith<IllegalArgumentException> {
            PhysicalWallLayoutTransitionDecision.Rejected("invalid_reason")
        }
        assertFailsWith<IllegalArgumentException> {
            TileWallPlacementOffset(alongWallStacks = Double.NaN)
        }
    }

    /** 建立固定回傳完成結果的測試 policy。 */
    private fun completedPolicy(
        layout: TileWallPhysicalLayout,
        phases: List<PhysicalWallLayoutTransitionPhase>,
    ): PhysicalWallLayoutPolicy = object : PhysicalWallLayoutPolicy {
        override fun createInitialLayout(
            context: InitialPhysicalWallLayoutContext,
        ): InitialPhysicalWallLayoutDecision = InitialPhysicalWallLayoutDecision.Completed(layout)

        override fun resolveTransition(
            context: PhysicalWallLayoutTransitionContext,
        ): PhysicalWallLayoutTransitionDecision = PhysicalWallLayoutTransitionDecision.Completed(layout, phases)
    }

    /** 建立共用的牌牆 transition 測試 context。 */
    private fun transitionContext(
        before: TableState,
        after: TableState,
        current: TileWallPhysicalLayout,
    ): PhysicalWallLayoutTransitionContext = PhysicalWallLayoutTransitionContext(
        tableStateBeforeAction = before,
        tableStateAfterAction = after,
        currentLayout = current,
        actorPlayerId = before.players.first().id,
        action = GameAction.Draw,
    )

    /** 建立具有獨立 Uuid 的測試牌。 */
    private fun testTile(): IdentifiedTile = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5))
}
