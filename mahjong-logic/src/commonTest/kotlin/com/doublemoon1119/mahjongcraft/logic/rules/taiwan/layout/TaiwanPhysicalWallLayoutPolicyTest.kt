package com.doublemoon1119.mahjongcraft.logic.rules.taiwan.layout

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TAIWAN_REVEAL_FLOWER_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanFlowerReplacement
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanPlayerTileAreaIds
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.PlayerTileAreas
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import com.doublemoon1119.mahjongcraft.logic.table.drawFromReservedWall
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
import kotlin.math.floor
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [TaiwanPhysicalWallLayoutPolicy] 的初始分界與補花、槓後補位測試。 */
class TaiwanPhysicalWallLayoutPolicyTest {
    /** 16 張保留牌正常疊成八墩，且與一般摸牌區保持可見分界。 */
    @Test
    fun `initial layout stacks sixteen reserved tiles and keeps a visible gap`() {
        listOf(false, true).forEach { useFlowerTiles ->
            val stacksPerSide = if (useFlowerTiles) STACKS_PER_SIDE_WITH_FLOWERS else STACKS_PER_SIDE_WITHOUT_FLOWERS
            allOpenings(stacksPerSide).forEach { opening ->
                val fixture = fixture(opening, useFlowerTiles)
                val reserved = fixture.wallLayout.reservedWallTiles

                reserved.chunked(2).forEach { pair ->
                    val first = fixture.layout.placements.getValue(pair[0].id)
                    val second = fixture.layout.placements.getValue(pair[1].id)
                    assertEquals(first.position.copy(layer = 0), second.position)
                    assertEquals(1, first.position.layer)
                    assertEquals(TileWallPlacementOffset(alongWallStacks = FULL_GAP), first.offset)
                    assertEquals(first.offset, second.offset)
                }
                assertNoOverlap(fixture.layout, fixture.tableState, "initial $opening flowers=$useFlowerTiles")
                assertNoCornerTransition(fixture.layout, fixture.tableState, stacksPerSide, "initial $opening flowers=$useFlowerTiles")
                assertTrue(nearestGap(fixture.layout, fixture.tableState) >= MINIMUM_GAP)
            }
        }
    }

    /** 含花牌時可連續完成八次補花與四次槓後補牌，並保持牌張身分、分界及無重疊。 */
    @Test
    fun `flower and kan replacements share twelve collision free transitions`() {
        openings().forEach { opening ->
            var fixture = fixture(opening, useFlowerTiles = true)
            val replenishmentIds = mutableListOf<Uuid>()

            repeat(MAX_FLOWER_AND_KAN_DRAWS) { index ->
                val before = fixture.tableState
                val supplemental = requireNotNull(drawFromReservedWall(before))
                replenishmentIds += supplemental.reservedWallTiles.last().id
                val after = before.copy(
                    tileWall = supplemental.tileWall,
                    reservedWallTiles = supplemental.reservedWallTiles,
                    physicalWallLayout = null,
                )
                val action = if (index < MAX_FLOWER_DRAWS) TAIWAN_REVEAL_FLOWER_ACTION else kanAction()
                val decision = assertIs<PhysicalWallLayoutTransitionDecision.Completed>(
                    value = fixture.policy.resolveSupplementalTransition(
                        context = PhysicalWallLayoutTransitionContext(
                            tableStateBeforeAction = before,
                            tableStateAfterAction = after,
                            currentLayout = fixture.layout,
                            actorPlayerId = before.currentPlayer.id,
                            action = action,
                        ),
                        drawNumber = index + 1,
                    ),
                )
                fixture = fixture.copy(
                    tableState = after.copy(physicalWallLayout = decision.layout),
                    layout = decision.layout,
                )
                assertEquals(if (index % 2 == 0) 2 else 1, decision.phases.size)
                assertNoOverlap(fixture.layout, fixture.tableState, "draw ${index + 1} at $opening")
                assertNoCornerTransition(
                    layout = fixture.layout,
                    state = fixture.tableState,
                    stacksPerSide = STACKS_PER_SIDE_WITH_FLOWERS,
                    stage = "draw ${index + 1} at $opening",
                )
                assertTrue(nearestGap(fixture.layout, fixture.tableState) >= MINIMUM_GAP)
            }

            assertEquals(fixture.originalLiveTailIds, replenishmentIds)
            assertEquals(16, fixture.tableState.reservedWallTiles.size)
        }
    }

    /**
     * 一般摸牌與補牌交錯到牌牆摸完，各種開門位置、有無花牌、補牌疏密都檢查：規則回報的本局剩餘範圍包含目前每張牌的位置，
     * 而且只會變小。桌上其他牌依這份範圍讓開牌牆，所以一局之中牌牆讓出的空間只會變大。
     */
    @Test
    fun `round reach covers the wall and only shrinks`() {
        listOf(true, false).forEach { useFlowerTiles ->
            val stacksPerSide = if (useFlowerTiles) STACKS_PER_SIDE_WITH_FLOWERS else STACKS_PER_SIDE_WITHOUT_FLOWERS
            val maximumDraws = if (useFlowerTiles) MAX_FLOWER_AND_KAN_DRAWS else MAX_KAN_DRAWS
            allOpenings(stacksPerSide).forEach { opening ->
                LIVE_DRAWS_PER_SUPPLEMENT.forEach { liveDrawsPerSupplement ->
                    val case = "$opening flowers=$useFlowerTiles every=$liveDrawsPerSupplement"
                    var fixture = fixture(opening, useFlowerTiles)
                    var reach = reachCoordinates(fixture.policy.roundReach(fixture.tableState), stacksPerSide)
                    var supplementalDraws = 0
                    var step = 0
                    while (fixture.tableState.tileWall.getAllTiles().isNotEmpty()) {
                        val before = fixture.tableState
                        val supplemental = supplementalDraws < maximumDraws && step % liveDrawsPerSupplement == 0
                        val after = if (supplemental) {
                            supplementalDraws++
                            withSupplementalDraw(before)
                        } else {
                            before.copy(tileWall = TileWall(before.tileWall.getAllTiles().drop(1)), physicalWallLayout = null)
                        }
                        val layout = when (
                            val result = fixture.policy.resolveTransitionValidated(
                                context = PhysicalWallLayoutTransitionContext(
                                    tableStateBeforeAction = before,
                                    tableStateAfterAction = after,
                                    currentLayout = fixture.layout,
                                    actorPlayerId = before.currentPlayer.id,
                                    action = if (supplemental) TAIWAN_REVEAL_FLOWER_ACTION else GameAction.Draw,
                                ),
                            )
                        ) {
                            is PhysicalWallLayoutTransitionDecision.Completed -> result.layout
                            PhysicalWallLayoutTransitionDecision.Unchanged -> fixture.layout
                            is PhysicalWallLayoutTransitionDecision.Rejected -> error("Step $step rejected for $case: ${result.reasonId}")
                        }
                        fixture = fixture.copy(tableState = after.copy(physicalWallLayout = layout), layout = layout)
                        val nextReach = reachCoordinates(fixture.policy.roundReach(fixture.tableState), stacksPerSide)
                        val current = reachCoordinates(layout.placements.values, stacksPerSide)
                        assertTrue(nextReach.containsAll(current), "Reach misses a wall tile at step $step for $case: ${current - nextReach}")
                        assertTrue(reach.containsAll(nextReach), "Reach grew at step $step for $case: ${nextReach - reach}")
                        reach = nextReach
                        step++
                    }
                    assertEquals(maximumDraws, supplementalDraws, "Not every supplemental draw happened for $case")
                }
            }
        }
    }

    /**
     * 一般摸牌區只剩最後一張時補花，那張牌補入保留區後一般摸牌區就空了：同墩上層早已被摸走，只移入那一張，
     * 不可因為沒有牌可以下降而拒絕補牌。
     */
    @Test
    fun `replenishing the last live tile on an odd draw moves only that tile`() {
        listOf(true, false).forEach { useFlowerTiles ->
            openings().forEach { opening ->
                val case = "$opening flowers=$useFlowerTiles"
                var fixture = fixture(opening, useFlowerTiles)
                while (fixture.tableState.tileWall.getAllTiles().size > 1) {
                    val before = fixture.tableState
                    val after = before.copy(tileWall = TileWall(before.tileWall.getAllTiles().drop(1)), physicalWallLayout = null)
                    val layout = when (
                        val result = fixture.policy.resolveTransitionValidated(
                            context = PhysicalWallLayoutTransitionContext(
                                tableStateBeforeAction = before,
                                tableStateAfterAction = after,
                                currentLayout = fixture.layout,
                                actorPlayerId = before.currentPlayer.id,
                                action = GameAction.Draw,
                            ),
                        )
                    ) {
                        is PhysicalWallLayoutTransitionDecision.Completed -> result.layout
                        PhysicalWallLayoutTransitionDecision.Unchanged -> fixture.layout
                        is PhysicalWallLayoutTransitionDecision.Rejected -> error("Live draw rejected for $case: ${result.reasonId}")
                    }
                    fixture = fixture.copy(tableState = after.copy(physicalWallLayout = layout), layout = layout)
                }
                val before = fixture.tableState
                val lastLiveTile = before.tileWall.getAllTiles().single()
                val after = withSupplementalDraw(before)

                val decision = assertIs<PhysicalWallLayoutTransitionDecision.Completed>(
                    value = fixture.policy.resolveTransitionValidated(
                        context = PhysicalWallLayoutTransitionContext(
                            tableStateBeforeAction = before,
                            tableStateAfterAction = after,
                            currentLayout = fixture.layout,
                            actorPlayerId = before.currentPlayer.id,
                            action = TAIWAN_REVEAL_FLOWER_ACTION,
                        ),
                    ),
                    message = case,
                )

                assertTrue(after.tileWall.getAllTiles().isEmpty(), case)
                assertEquals(listOf(lastLiveTile.id), decision.phases.flatMap { phase -> phase.moves.map { it.tileId } }, case)
                assertEquals(16, after.reservedWallTiles.size, case)
                assertNoOverlap(decision.layout, after.copy(physicalWallLayout = decision.layout), "last live tile at $case")
            }
        }
    }

    /**
     * 從保留區補一張牌，並在第一位玩家的花牌區多放一張牌，讓規則算出的補牌次數加一。
     *
     * @param state 補牌前的桌況。
     * @return 補牌後、尚未套用實體布局的桌況。
     */
    private fun withSupplementalDraw(state: TableState): TableState {
        val drawn = requireNotNull(drawFromReservedWall(state))
        val player = state.players.first()
        val flowers = player.tileAreas[TaiwanPlayerTileAreaIds.FLOWERS]?.tiles.orEmpty() +
            FakeIdentifiedTileFactory.create(Tile.Extension(TaiwanTileTypes.ALL.first()))
        return state.copy(
            players = state.players.toMutableList().apply {
                this[0] = player.copy(tileAreas = PlayerTileAreas.EMPTY.withArea(TaiwanPlayerTileAreaIds.FLOWERS, flowers))
            },
            tileWall = drawn.tileWall,
            reservedWallTiles = drawn.reservedWallTiles,
            physicalWallLayout = null,
        )
    }

    /** 不使用花牌時只預留四次槓後補牌，第四次後仍保有完整布局。 */
    @Test
    fun `wall without flowers supports four kan replacements`() {
        var fixture = fixture(WallOpening(2, 8), useFlowerTiles = false)

        repeat(MAX_KAN_DRAWS) { index ->
            val before = fixture.tableState
            val supplemental = requireNotNull(drawFromReservedWall(before))
            val after = before.copy(
                tileWall = supplemental.tileWall,
                reservedWallTiles = supplemental.reservedWallTiles,
                physicalWallLayout = null,
            )
            val decision = assertIs<PhysicalWallLayoutTransitionDecision.Completed>(
                value = fixture.policy.resolveSupplementalTransition(
                    context = PhysicalWallLayoutTransitionContext(
                        tableStateBeforeAction = before,
                        tableStateAfterAction = after,
                        currentLayout = fixture.layout,
                        actorPlayerId = before.currentPlayer.id,
                        action = kanAction(),
                    ),
                    drawNumber = index + 1,
                ),
            )
            fixture = fixture.copy(after.copy(physicalWallLayout = decision.layout), layout = decision.layout)
        }

        assertEquals(
            expected = (fixture.tableState.tileWall.getAllTiles() + fixture.tableState.reservedWallTiles).map { it.id }.toSet(),
            actual = fixture.layout.placements.keys,
        )
        assertNoOverlap(fixture.layout, fixture.tableState, "four kan wall")
    }

    /** 一般動作路徑會依公開花牌數辨認補牌，無須由呼叫端傳入補牌序號。 */
    @Test
    fun `normal transition counts revealed flowers`() {
        val fixture = fixture(WallOpening(0, 2), useFlowerTiles = true)
        val before = fixture.tableState
        val supplement = requireNotNull(drawFromReservedWall(before))
        val flower = FakeIdentifiedTileFactory.create(Tile.Extension(TaiwanTileTypes.ALL.first()))
        val player = before.players.first()
        val updatedPlayer = player.copy(
            tileAreas = PlayerTileAreas.EMPTY.withArea(TaiwanPlayerTileAreaIds.FLOWERS, listOf(flower)),
        )
        val after = before.copy(
            players = before.players.toMutableList().apply { this[0] = updatedPlayer },
            tileWall = supplement.tileWall,
            reservedWallTiles = supplement.reservedWallTiles,
            physicalWallLayout = null,
        )

        val decision = assertIs<PhysicalWallLayoutTransitionDecision.Completed>(
            value = fixture.policy.resolveTransitionValidated(
                context = PhysicalWallLayoutTransitionContext(
                    tableStateBeforeAction = before,
                    tableStateAfterAction = after,
                    currentLayout = fixture.layout,
                    actorPlayerId = player.id,
                    action = TAIWAN_REVEAL_FLOWER_ACTION,
                ),
            ),
        )

        assertNoOverlap(decision.layout, after, "normal flower transition")
        assertTrue(nearestGap(decision.layout, after) >= MINIMUM_GAP)
    }

    /** 局前一次亮出多張花牌後逐張補牌，會依實際補牌進度更新權威實體布局。 */
    @Test
    fun `opening flower replacements update physical layout in order`() {
        val fixture = fixture(WallOpening(1, 5), useFlowerTiles = true)
        val flowers = TaiwanTileTypes.ALL.take(2).map { type -> FakeIdentifiedTileFactory.create(Tile.Extension(type)) }
        val player = fixture.tableState.players.first()
        val before = fixture.tableState.copy(
            players = fixture.tableState.players.toMutableList().apply {
                this[0] = player.copy(
                    tileAreas = PlayerTileAreas.EMPTY.withArea(TaiwanPlayerTileAreaIds.FLOWERS, flowers),
                )
            },
            turnPhase = TurnPhase.AwaitingAction,
        )

        val first = requireNotNull(TaiwanFlowerReplacement.supplement(before, player.id, replacementsRemaining = 2))
        val second = requireNotNull(TaiwanFlowerReplacement.supplement(first.tableState, player.id, replacementsRemaining = 1))
        val layout = requireNotNull(second.tableState.physicalWallLayout)

        assertNoOverlap(layout, second.tableState, "opening flower replacements")
        assertTrue(nearestGap(layout, second.tableState) >= MINIMUM_GAP)
        assertEquals(before.reservedWallTiles.take(2).map { it.id }, listOf(first.replacementTile.id, second.replacementTile.id))
    }

    /** 建立完成初次發牌的台麻桌況與實體布局。 */
    private fun fixture(opening: WallOpening, useFlowerTiles: Boolean): Fixture {
        val config = TaiwanRuleConfig(useFlowerTiles = useFlowerTiles)
        val tileCount = if (useFlowerTiles) 144 else 136
        val wallLayout = TaiwanWallLayout(config).resolve(
            shuffledTiles = List(tileCount) { FakeIdentifiedTileFactory.create(Tile.Honor.East) },
            opening = opening,
        )
        val policy = TaiwanPhysicalWallLayoutPolicy(config)
        val completeLayout = assertIs<InitialPhysicalWallLayoutDecision.Completed>(
            value = policy.createInitialLayoutValidated(
                context = InitialPhysicalWallLayoutContext(
                    wallLayout = wallLayout,
                    wallOpening = opening,
                    occupiedTileIds = (wallLayout.drawOrder.drop(INITIAL_DEAL_TILE_COUNT) + wallLayout.reservedWallTiles)
                        .mapTo(mutableSetOf()) { it.id },
                ),
            ),
        ).layout
        val remainingLiveTiles = wallLayout.drawOrder.drop(INITIAL_DEAL_TILE_COUNT)
        val remainingIds = (remainingLiveTiles + wallLayout.reservedWallTiles).mapTo(mutableSetOf()) { it.id }
        val layout = TileWallPhysicalLayout(completeLayout.placements.filterKeys { it in remainingIds })
        val state = FakeTableStateFactory.create(
            config = config,
            tileWall = TileWall(remainingLiveTiles),
            reservedWallTiles = wallLayout.reservedWallTiles,
            physicalWallLayout = layout,
        )
        return Fixture(
            tableState = state,
            layout = layout,
            policy = policy,
            wallLayout = wallLayout,
            originalLiveTailIds = remainingLiveTiles.takeLast(MAX_FLOWER_AND_KAN_DRAWS).reversed().map { it.id },
        )
    }

    /** 同一面同一層不得有中心距小於一墩的牌。 */
    private fun assertNoOverlap(layout: TileWallPhysicalLayout, state: TableState, stage: String) {
        val presentIds = (state.tileWall.getAllTiles() + state.reservedWallTiles).mapTo(mutableSetOf()) { it.id }
        val overlaps = layout.placements.filterKeys { it in presentIds }
            .values
            .groupBy { it.position.side to it.position.layer }
            .flatMap { (_, placements) ->
                placements.map(::wallCoordinate).sorted().zipWithNext().filter { (left, right) -> right - left < 1.0 }
            }
        assertTrue(overlaps.isEmpty(), "Expected no overlapping wall tiles at $stage, but found $overlaps.")
    }

    /** 保留牌套用位移後不得停在兩面牌牆之間的轉角插值區。 */
    private fun assertNoCornerTransition(
        layout: TileWallPhysicalLayout,
        state: TableState,
        stacksPerSide: Int,
        stage: String,
    ) {
        val reservedIds = state.reservedWallTiles.mapTo(mutableSetOf()) { it.id }
        val cornerTiles = layout.placements
            .filterKeys { it in reservedIds }
            .filterValues { placement ->
                val target = placement.position.stack + placement.offset.alongWallStacks
                val lower = floor(target).toInt()
                target != lower.toDouble() && lower % stacksPerSide == stacksPerSide - 1
            }

        assertTrue(
            actual = cornerTiles.isEmpty(),
            message = "Expected no reserved tiles in corner transition slots at $stage, but found ${cornerTiles.keys}.",
        )
    }

    /** 保留區與一般摸牌區最接近牌張之間的空隙。 */
    private fun nearestGap(layout: TileWallPhysicalLayout, state: TableState): Double {
        val reservedIds = state.reservedWallTiles.mapTo(mutableSetOf()) { it.id }
        val liveIds = state.tileWall.getAllTiles().mapTo(mutableSetOf()) { it.id }
        val reserved = layout.placements.filterKeys { it in reservedIds }.values
        val live = layout.placements.filterKeys { it in liveIds }.values
        return reserved.flatMap { reservedPlacement ->
            live.filter {
                it.position.side == reservedPlacement.position.side &&
                    it.position.layer == reservedPlacement.position.layer
            }.map { livePlacement -> abs(wallCoordinate(reservedPlacement) - wallCoordinate(livePlacement)) - 1.0 }
        }.minOrNull() ?: Double.POSITIVE_INFINITY
    }

    /**
     * 把位置換成繞牌牆一圈的座標，跨面的位移與相鄰面的基本格位得到相同座標。
     *
     * @param placements 牌牆位置。
     * @param stacksPerSide 每面墩數。
     * @return 層數與千分之一墩精度的環狀座標。
     */
    private fun reachCoordinates(placements: Collection<TileWallPlacement>, stacksPerSide: Int): Set<Pair<Int, Long>> {
        val total = WALL_SIDE_COUNT * stacksPerSide * COORDINATE_SCALE
        return placements.mapTo(mutableSetOf()) { placement ->
            val coordinate = (placement.position.side * stacksPerSide + wallCoordinate(placement)) * COORDINATE_SCALE
            placement.position.layer to round(coordinate).toLong().mod(total.toLong())
        }
    }

    /** Placement 沿牆方向的中心座標。 */
    private fun wallCoordinate(placement: TileWallPlacement): Double = placement.position.stack + placement.offset.alongWallStacks

    /** 建立不依賴手牌內容的暗槓動作。 */
    private fun kanAction(): GameAction.Kan = GameAction.Kan(GameAction.KanType.CLOSED_KAN, Uuid.random(), emptyList())

    /** 涵蓋四面牌牆與不同開門墩的測試位置。 */
    private fun openings(): List<WallOpening> = listOf(
        WallOpening(0, 2),
        WallOpening(1, 5),
        WallOpening(2, 8),
        WallOpening(3, 12),
    )

    /** 列出指定每面墩數的全部合法開門位置。 */
    private fun allOpenings(stacksPerSide: Int): List<WallOpening> = (0 until WALL_SIDE_COUNT).flatMap { side ->
        (1..stacksPerSide).map { stack -> WallOpening(side, stack) }
    }

    /** 一組可逐次套用補牌的測試資料。 */
    private data class Fixture(
        val tableState: TableState,
        val layout: TileWallPhysicalLayout,
        val policy: TaiwanPhysicalWallLayoutPolicy,
        val wallLayout: TileWallLayoutResult,
        val originalLiveTailIds: List<Uuid>,
    )

    private companion object {
        /** 四家各 16 張加上莊家開局摸牌。 */
        const val INITIAL_DEAL_TILE_COUNT = 65

        /** 四面牌牆。 */
        const val WALL_SIDE_COUNT = 4

        /** 模擬時每摸幾張活牌補一次牌；1 代表連續補牌。 */
        val LIVE_DRAWS_PER_SUPPLEMENT: List<Int> = listOf(1, 2, 4)

        /** 環狀座標的精度倍數。 */
        const val COORDINATE_SCALE = 1000

        /** 不使用花牌時每面的墩數。 */
        const val STACKS_PER_SIDE_WITHOUT_FLOWERS = 17

        /** 使用花牌時每面的墩數。 */
        const val STACKS_PER_SIDE_WITH_FLOWERS = 18

        /** 一副牌的花牌數。 */
        const val MAX_FLOWER_DRAWS = 8

        /** 四槓流局前最多完成的槓後補牌數。 */
        const val MAX_KAN_DRAWS = 4

        /** 最多補花與槓後補牌次數。 */
        const val MAX_FLOWER_AND_KAN_DRAWS = MAX_FLOWER_DRAWS + MAX_KAN_DRAWS

        /** 初始保留區分界位移。 */
        const val FULL_GAP = 2.0

        /** 補位期間至少保留的分界寬度。 */
        const val MINIMUM_GAP = 0.5
    }
}
