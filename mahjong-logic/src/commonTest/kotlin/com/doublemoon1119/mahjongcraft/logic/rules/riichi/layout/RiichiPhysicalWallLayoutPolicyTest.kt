package com.doublemoon1119.mahjongcraft.logic.rules.riichi.layout

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiSupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawContext
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.createInitialLayoutValidated
import com.doublemoon1119.mahjongcraft.logic.table.layout.resolveTransitionValidated
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.math.abs
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

    /**
     * 驗證整局牌牆不存在空間重疊。
     *
     * 牌張寬度為一墩，因此同一面同一層的任兩張牌中心距不得小於一墩。這條不變式涵蓋開局、開局發牌後與
     * 四次槓的每一個時點；`TileWallPhysicalLayout` 本身只擋完全相同的 placement，帶位移的半墩重疊會通過。
     */
    @Test
    fun `no two wall tiles ever overlap in space`() {
        openings().forEach { opening ->
            playFourKans(opening) { stage, layout, state ->
                val overlaps = overlappingPairs(layout, state)
                assertTrue(
                    overlaps.isEmpty(),
                    "Expected no overlapping wall tiles at $stage for $opening, but found $overlaps.",
                )
            }
        }
    }

    /**
     * 驗證王牌區與活牌區之間的分界始終存在。
     *
     * 奇數次槓把一張牌（寬一墩）補進分界，分界因此正好少一墩；偶數次槓把該墩用完、活牌整排後退一墩，
     * 分界正好回復一墩。任何時點都必須至少留半墩，不得貼齊或重疊。
     *
     * 分界的絕對寬度依開門位置而異（活牌末端不一定剛好抵著王牌區），因此這裡驗證的是每次槓造成的變化量
     * 與下界，不是固定寬度。
     */
    @Test
    fun `keeps a visible gap between the dead wall and the live wall`() {
        openings().forEach { opening ->
            var previousGap: Double? = null
            playFourKans(opening) { stage, layout, state ->
                val gap = nearestGap(layout, state) ?: return@playFourKans
                assertTrue(
                    gap >= MINIMUM_GAP,
                    "Expected at least $MINIMUM_GAP stacks of gap at $stage for $opening, but measured $gap.",
                )
                val expected = when {
                    previousGap == null -> gap
                    stage.removePrefix("kan").toInt() % 2 == 1 -> previousGap!! - 1.0
                    else -> previousGap!! + 1.0
                }
                assertEquals(
                    expected,
                    gap,
                    "Expected a gap of $expected stacks at $stage for $opening, but measured $gap.",
                )
                previousGap = gap
            }
        }
    }

    /**
     * 模擬開局發牌與四次槓，在每個時點以目前布局及仍在牌牆中的牌呼叫 [check]。
     *
     * 摸走的牌會同步從布局移除，比照 production 每次 `Draw` transition 的修剪行為；不修剪的話
     * [resolveTransitionValidated] 會因為布局多出已離開牌牆的牌而拒絕。
     *
     * 第一個檢查點是開局發牌完成後，不是剛建立布局時：初始布局是以「開局發牌已完成」為前提規劃的
     * （見 [initialPhysicalLayout] 傳入的 `occupiedTileIds`），發牌前那 53 張牌仍名義上佔著牌牆位置，
     * 那個瞬間的重疊在玩家看到桌面之前就已經不存在。
     */
    private fun playFourKans(
        opening: WallOpening,
        check: (stage: String, layout: TileWallPhysicalLayout, tableState: TableState) -> Unit,
    ) {
        val wallLayout = createWallLayout(opening)
        var tableState = FakeTableStateFactory.create(
            config = RiichiRuleConfig(),
            tileWall = TileWall(wallLayout.drawOrder),
            initialDeadWall = wallLayout.reservedWallTiles,
            dynamicRuleState = RiichiDynamicState(),
        )
        var layout = initialPhysicalLayout(wallLayout, opening)

        repeat(INITIAL_DEAL_TILE_COUNT) {
            tableState = tableState.copy(tileWall = tableState.tileWall.draw().wall)
        }
        layout = TileWallPhysicalLayout(layout.placements.filterKeys { it in presentTileIds(tableState) })
        check("dealt", layout, tableState)

        repeat(4) { index ->
            val action = kanAction()
            val supplemental = assertIs<SupplementalDrawDecision.Completed>(
                RiichiSupplementalDrawPolicy.resolve(
                    SupplementalDrawContext(tableState, tableState, tableState.currentPlayer.id, action),
                ),
            )
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
                        currentLayout = layout,
                        actorPlayerId = tableState.currentPlayer.id,
                        action = action,
                    ),
                ),
            )
            layout = transition.layout
            tableState = updatedState
            check("kan${index + 1}", layout, tableState)
        }
    }

    /** 仍在牌牆中（活牌與王牌）的牌張 ID。 */
    private fun presentTileIds(tableState: TableState): Set<Uuid> = (tableState.tileWall.getAllTiles() + tableState.reservedWallTiles).mapTo(mutableSetOf()) { it.id }

    /** 同一面同一層中心距小於一墩的相鄰牌張對。 */
    private fun overlappingPairs(layout: TileWallPhysicalLayout, tableState: TableState): List<String> = layout.placements.filterKeys { it in presentTileIds(tableState) }
        .values
        .groupBy { it.position.side to it.position.layer }
        .flatMap { (lane, placements) ->
            placements.map(::wallCoordinate)
                .sorted()
                .zipWithNext()
                .filter { (left, right) -> right - left < 1.0 }
                .map { (left, right) -> "side=${lane.first} layer=${lane.second} $left/$right" }
        }

    /**
     * 王牌區與活牌區最接近的一對牌之間的空隙寬度（墩）。
     *
     * 兩區不在同一面時沒有可比較的配對，回傳 `null`。
     */
    private fun nearestGap(layout: TileWallPhysicalLayout, tableState: TableState): Double? {
        val liveIds = tableState.tileWall.getAllTiles().mapTo(mutableSetOf()) { it.id }
        val deadPlacements = layout.placements.filterKeys { it in tableState.reservedWallTiles.map { tile -> tile.id } }.values
        val livePlacements = layout.placements.filterKeys { it in liveIds }.values
        return deadPlacements.flatMap { dead ->
            livePlacements
                .filter { it.position.side == dead.position.side && it.position.layer == dead.position.layer }
                .map { abs(wallCoordinate(dead) - wallCoordinate(it)) - 1.0 }
        }.minOrNull()
    }

    /** Placement 沿牆方向的中心座標（墩）。 */
    private fun wallCoordinate(placement: TileWallPlacement): Double = placement.position.stack + placement.offset.alongWallStacks

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

    /** 日麻王牌區整體朝開門空位平移的分界位移。 */
    private val deadWallOffset = TileWallPlacementOffset(alongWallStacks = FULL_GAP)

    /** 四人日麻初次發牌後離開牌牆的牌張數。 */
    private companion object {
        const val INITIAL_DEAL_TILE_COUNT = 53

        /** 王牌區整體朝開門空位平移的墩數，同時是活牌整排尚未後退時的分界寬度。 */
        const val FULL_GAP = 1.5

        /** 奇數次槓把一張牌補進分界後仍必須留下的最小寬度（墩）。 */
        const val MINIMUM_GAP = 0.5
    }
}
