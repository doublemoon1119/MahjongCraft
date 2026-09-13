package com.doublemoon1119.mahjongcraft.logic.rules.riichi.layout

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiSupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawContext
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.createInitialLayoutValidated
import com.doublemoon1119.mahjongcraft.logic.table.layout.resolveTransitionValidated
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [RiichiPhysicalWallLayoutPolicy] 的初始王牌位置與四次槓後補位測試。 */
class RiichiPhysicalWallLayoutPolicyTest {
    /** 驗證所有王牌集中於開門面，且第一張嶺上牌位於第二張外側下層。 */
    @Test
    fun `initial layout separates the dead wall and lowers first rinshan tile outside the break`() {
        openings().forEach { opening ->
            val wallLayout = createWallLayout(opening)
            val layout = initialPhysicalLayout(wallLayout, opening)
            val firstRinshan = wallLayout.reservedWallTiles[0]
            val secondRinshan = wallLayout.reservedWallTiles[1]
            val firstPlacement = layout.placements.getValue(firstRinshan.id)
            val secondPlacement = layout.placements.getValue(secondRinshan.id)

            assertEquals(0, firstPlacement.position.layer)
            assertEquals(opening.wallSideOffsetFromDealer, firstPlacement.position.side)
            assertEquals(firstPlacement.position.side, secondPlacement.position.side)
            assertEquals(secondPlacement.position.stack + 1, firstPlacement.position.stack)
            assertEquals(deadWallOffset, firstPlacement.offset)
            wallLayout.reservedWallTiles.drop(1).forEach { tile ->
                val placement = layout.placements.getValue(tile.id)
                assertEquals(opening.wallSideOffsetFromDealer, placement.position.side)
                assertEquals(deadWallOffset, placement.offset)
            }
            wallLayout.drawOrder.forEach { tile ->
                assertEquals(TileWallPlacementOffset.Zero, layout.placements.getValue(tile.id).offset)
            }
        }
    }

    /** 驗證四次槓依序形成半墩與完整墩，且每次移動的 UUID 與補牌 policy 完全相同。 */
    @Test
    fun `four kan transitions follow supplemental draw tile identities and alternating stack shapes`() {
        openings().forEach { opening ->
            val wallLayout = createWallLayout(opening)
            var tableState = FakeTableStateFactory.create(
                config = RiichiRuleConfig(),
                tileWall = TileWall(wallLayout.drawOrder),
                initialDeadWall = wallLayout.reservedWallTiles,
                dynamicRuleState = RiichiDynamicState(),
            )
            var physicalLayout = initialPhysicalLayout(wallLayout, opening)
            val originalRinshanIds = wallLayout.reservedWallTiles.take(4).map { it.id }
            val replenishmentIds = mutableListOf<Uuid>()

            repeat(4) { index ->
                val action = kanAction()
                val supplemental = assertIs<SupplementalDrawDecision.Completed>(
                    RiichiSupplementalDrawPolicy.resolve(
                        SupplementalDrawContext(tableState, tableState, tableState.currentPlayer.id, action),
                    ),
                )
                val replenishmentId = supplemental.reservedWallTiles.last().id
                replenishmentIds += replenishmentId
                val updatedState = tableState.copy(
                    tileWall = supplemental.tileWall,
                    initialDeadWall = supplemental.reservedWallTiles,
                    dynamicRuleState = supplemental.dynamicRuleState,
                )
                val transition = assertIs<PhysicalWallLayoutTransitionDecision.Completed>(
                    RiichiPhysicalWallLayoutPolicy.resolveTransitionValidated(
                        PhysicalWallLayoutTransitionContext(
                            tableStateBeforeAction = tableState,
                            tableStateAfterAction = updatedState,
                            currentLayout = physicalLayout,
                            actorPlayerId = tableState.currentPlayer.id,
                            action = action,
                        ),
                    ),
                )

                assertTrue(originalRinshanIds[index] !in transition.layout.placements)
                assertEquals(if (index % 2 == 0) 2 else 1, transition.phases.size)
                assertTrue(transition.phases.flatMap { it.moves }.any { it.tileId == replenishmentId })
                if (index % 2 == 0) {
                    assertEquals(replenishmentId, transition.phases[0].moves.single().tileId)
                    assertEquals(updatedState.tileWall.getAllTiles().last().id, transition.phases[1].moves.single().tileId)
                }
                val replenishmentPlacement = transition.layout.placements.getValue(replenishmentId)
                assertEquals(if (index % 2 == 0) 0 else 1, replenishmentPlacement.position.layer)
                assertEquals(deadWallOffset, replenishmentPlacement.offset)

                physicalLayout = transition.layout
                tableState = updatedState
            }

            assertEquals(wallLayout.drawOrder.takeLast(4).reversed().map { it.id }, replenishmentIds)
            assertEquals(14, tableState.reservedWallTiles.size)
            assertEquals(tableState.reservedWallTiles.map { it.id }.toSet(), physicalLayout.placements.keys - tableState.tileWall.getAllTiles().map { it.id }.toSet())
        }
    }

    /** 驗證非槓動作只移除摸走的牌，不移動其餘 placement。 */
    @Test
    fun `non kan transition only removes departed wall tile`() {
        val wallLayout = createWallLayout(WallOpening(0, 1))
        val initialState = FakeTableStateFactory.create(
            config = RiichiRuleConfig(),
            tileWall = TileWall(wallLayout.drawOrder),
            initialDeadWall = wallLayout.reservedWallTiles,
            dynamicRuleState = RiichiDynamicState(),
        )
        val draw = initialState.tileWall.draw()
        val updatedState = initialState.copy(tileWall = draw.wall)
        val initialLayout = initialPhysicalLayout(wallLayout, WallOpening(0, 1))

        val transition = assertIs<PhysicalWallLayoutTransitionDecision.Completed>(
            RiichiPhysicalWallLayoutPolicy.resolveTransitionValidated(
                PhysicalWallLayoutTransitionContext(
                    initialState,
                    updatedState,
                    initialLayout,
                    initialState.currentPlayer.id,
                    GameAction.Draw,
                ),
            ),
        )

        assertTrue(draw.tile?.id !in transition.layout.placements)
        assertTrue(transition.phases.isEmpty())
    }

    /** 建立可覆蓋一般開門、面內邊界與跨面邊界的日麻布局。 */
    private fun openings(): List<WallOpening> = listOf(
        WallOpening(0, 2),
        WallOpening(1, 5),
        WallOpening(2, 8),
        WallOpening(3, 12),
    )

    /** 建立指定開門位置的固定 136 張測試牌牆。 */
    private fun createWallLayout(opening: WallOpening): TileWallLayoutResult = RiichiWallLayout(RiichiRuleConfig()).resolve(
        List(136) { FakeIdentifiedTileFactory.create(Tile.Honor.East) },
        opening,
    )

    /** 取得並斷言有效的日麻初始實體布局。 */
    private fun initialPhysicalLayout(
        wallLayout: TileWallLayoutResult,
        opening: WallOpening,
    ): TileWallPhysicalLayout = assertIs<InitialPhysicalWallLayoutDecision.Completed>(
        RiichiPhysicalWallLayoutPolicy.createInitialLayoutValidated(
            InitialPhysicalWallLayoutContext(
                wallLayout,
                opening,
                (wallLayout.drawOrder.drop(INITIAL_DEAL_TILE_COUNT) + wallLayout.reservedWallTiles)
                    .mapTo(mutableSetOf()) { it.id },
            ),
        ),
    ).layout

    /** 建立不依賴實際手牌內容的暗槓動作識別。 */
    private fun kanAction(): GameAction.Kan = GameAction.Kan(GameAction.KanType.CLOSED_KAN, Uuid.random(), emptyList())

    /** 日麻王牌區使用的四分之一墩分界位移。 */
    private val deadWallOffset = TileWallPlacementOffset(alongWallStacks = 0.25)

    /** 四人日麻初次發牌後離開牌牆的牌張數。 */
    private companion object {
        const val INITIAL_DEAL_TILE_COUNT = 53
    }
}
