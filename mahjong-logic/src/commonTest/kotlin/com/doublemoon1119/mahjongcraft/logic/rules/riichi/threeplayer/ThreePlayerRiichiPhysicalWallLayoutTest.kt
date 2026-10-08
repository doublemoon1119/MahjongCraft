package com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PULL_NORTH_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiSupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.layout.RiichiPhysicalWallLayoutPolicy
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawContext
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.createInitialLayoutValidated
import com.doublemoon1119.mahjongcraft.logic.table.layout.resolveTransitionValidated
import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 三人日麻 18 張王牌的實體牌牆布局：開局與八次補牌（拔北與槓交錯）都不重疊、王牌與活牌之間留有分界。 */
class ThreePlayerRiichiPhysicalWallLayoutTest {
    private val config = ThreePlayerRiichiRuleConfig()

    /** 驗證每一種骰子總和的開門位置都能建立布局，並完成八次補牌而不讓牌重疊。 */
    @Test
    fun `every opening survives eight replacement draws without overlapping tiles`() {
        (2..12).forEach { total ->
            val first = (total - 1).coerceAtMost(6)
            val opening = ThreePlayerRiichiWallOpeningPolicy.resolve(DiceRollResult.of(listOf(first, total - first)))
            val wallLayout = ThreePlayerRiichiWallLayout(config).resolve(
                List(108) { FakeIdentifiedTileFactory.create(Tile.Honor.East) },
                opening,
            )
            var tableState = FakeTableStateFactory.create(
                config = config,
                tileWall = TileWall(wallLayout.drawOrder),
                reservedWallTiles = wallLayout.reservedWallTiles,
                dynamicRuleState = RiichiDynamicState(),
            )
            var layout = assertIs<InitialPhysicalWallLayoutDecision.Completed>(
                RiichiPhysicalWallLayoutPolicy(rinshanTileCount = config.rinshanTileCount).createInitialLayoutValidated(
                    InitialPhysicalWallLayoutContext(
                        wallLayout,
                        opening,
                        (wallLayout.drawOrder.drop(INITIAL_DEAL_TILE_COUNT) + wallLayout.reservedWallTiles).mapTo(mutableSetOf()) { it.id },
                    ),
                ),
                "Initial layout failed for dice total $total",
            ).layout
            repeat(INITIAL_DEAL_TILE_COUNT) { tableState = tableState.copy(tileWall = tableState.tileWall.draw().wall) }
            layout = TileWallPhysicalLayout(layout.placements.filterKeys { it in presentTileIds(tableState) })
            assertNoOverlap(layout, tableState, "total $total dealt")

            val actions = List(RiichiSupplementalDrawPolicy.MAX_SUPPLEMENTAL_DRAWS) { listOf(PULL_NORTH_GAME_ACTION, kanAction()) }.flatten()
            actions.forEachIndexed { index, action ->
                val supplemental = assertIs<SupplementalDrawDecision.Completed>(
                    RiichiSupplementalDrawPolicy.resolve(SupplementalDrawContext(tableState, tableState, tableState.currentPlayer.id, action)),
                )
                val updatedState = tableState.copy(
                    tileWall = supplemental.tileWall,
                    reservedWallTiles = supplemental.reservedWallTiles,
                    dynamicRuleState = supplemental.dynamicRuleState,
                )
                layout = assertIs<PhysicalWallLayoutTransitionDecision.Completed>(
                    RiichiPhysicalWallLayoutPolicy(rinshanTileCount = config.rinshanTileCount).resolveTransitionValidated(
                        PhysicalWallLayoutTransitionContext(
                            tableStateBeforeAction = tableState,
                            tableStateAfterAction = updatedState,
                            currentLayout = layout,
                            actorPlayerId = tableState.currentPlayer.id,
                            action = action,
                        ),
                    ),
                    "Transition ${index + 1} failed for dice total $total",
                ).layout
                tableState = updatedState
                assertNoOverlap(layout, tableState, "total $total draw ${index + 1}")
            }
        }
    }

    /** 同一面同一層任兩張牌中心距不得小於一墩。 */
    private fun assertNoOverlap(layout: TileWallPhysicalLayout, tableState: TableState, stage: String) {
        val overlaps = layout.placements.filterKeys { it in presentTileIds(tableState) }
            .values
            .groupBy { it.position.side to it.position.layer }
            .flatMap { (_, placements) ->
                placements.map(::wallCoordinate).sorted().zipWithNext().filter { (left, right) -> right - left < 1.0 }
            }
        assertTrue(overlaps.isEmpty(), "Overlapping wall tiles at $stage: $overlaps")
    }

    /** 仍在牌牆中（活牌與王牌）的牌張 ID。 */
    private fun presentTileIds(tableState: TableState): Set<Uuid> = (tableState.tileWall.getAllTiles() + tableState.reservedWallTiles).mapTo(mutableSetOf()) { it.id }

    /** Placement 沿牆方向的中心座標（墩）。 */
    private fun wallCoordinate(placement: TileWallPlacement): Double = placement.position.stack + placement.offset.alongWallStacks

    /** 不依賴實際手牌內容的暗槓動作。 */
    private fun kanAction(): GameAction.Kan = GameAction.Kan(GameAction.KanType.CLOSED_KAN, Uuid.random(), emptyList())

    private companion object {
        /** 三人日麻開局發牌後離開牌牆的牌張數（每人 13 張加莊家第一次摸牌）。 */
        const val INITIAL_DEAL_TILE_COUNT = 40
    }
}
