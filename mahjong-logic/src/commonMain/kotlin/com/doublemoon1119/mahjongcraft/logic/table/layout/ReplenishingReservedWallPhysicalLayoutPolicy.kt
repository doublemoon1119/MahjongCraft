package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.math.ceil
import kotlin.uuid.Uuid

/**
 * 將保留區集中到開門附近，並在每次補牌後把一般摸牌區尾端補回保留區的共用實體布局。
 *
 * 規則仍負責決定哪些動作需要補牌、保留區內牌張的用途，以及已完成的補牌次數。本類別只核對實際牌區
 * 變化並產生位置與移動階段。
 *
 * @property maximumSupplementalDrawCount 一局最多會從保留區補牌的次數。
 * @property initialShape 保留區靠缺口一端的初始堆疊方式。
 * @property trackScope 保留區軌道是否可以跨越桌角。
 * @property sideCount 牌牆面數。
 * @property stacksPerSide 每面牌牆的墩數。
 * @property reservedWallGapStacks 保留區沿牌牆方向形成分界的位移墩數。
 * @property completedDrawCount 從桌況取得已完成補牌次數的規則函式。
 * @property invalidStateReasonId 初始資料不符合保留區契約時的拒絕原因。
 * @property noTrackReasonId 牌牆找不到可用連續軌道時的拒絕原因。
 * @property invalidTransitionReasonId 補牌前後資料不一致時的拒絕原因。
 */
internal class ReplenishingReservedWallPhysicalLayoutPolicy(
    private val maximumSupplementalDrawCount: Int,
    private val initialShape: ReservedWallInitialShape,
    private val trackScope: ReservedWallTrackScope,
    private val sideCount: Int,
    private val stacksPerSide: Int,
    private val reservedWallGapStacks: Double,
    private val completedDrawCount: (TableState) -> Int,
    private val invalidStateReasonId: String,
    private val noTrackReasonId: String,
    private val invalidTransitionReasonId: String,
) : PhysicalWallLayoutPolicy {
    init {
        require(maximumSupplementalDrawCount > 0) { "Maximum supplemental draw count must be positive" }
        require(sideCount > 0) { "Wall side count must be positive" }
        require(stacksPerSide > 0) { "Stacks per side must be positive" }
        require(reservedWallGapStacks > 0.0 && reservedWallGapStacks.isFinite()) {
            "Reserved wall gap must be finite and positive"
        }
    }

    /** 建立帶有可見分界，並預留後續補位軌道的初始布局。 */
    override fun createInitialLayout(context: InitialPhysicalWallLayoutContext): InitialPhysicalWallLayoutDecision {
        val wallLayout = context.wallLayout
        val reservedTileCount = wallLayout.reservedWallTiles.size
        if (reservedTileCount <= maximumSupplementalDrawCount || reservedTileCount % TILES_PER_STACK != 0) {
            return InitialPhysicalWallLayoutDecision.Rejected(invalidStateReasonId)
        }
        val reservedIds = wallLayout.reservedWallTiles.mapTo(mutableSetOf()) { it.id }
        val occupiedPositions = context.occupiedTileIds
            .asSequence()
            .filter { tileId -> tileId !in reservedIds }
            .mapNotNull(wallLayout.structure::get)
            .toSet()
        if (wallLayout.sideCount != sideCount || wallLayout.stacksPerSideOrNull() != stacksPerSide) {
            return InitialPhysicalWallLayoutDecision.Rejected(invalidStateReasonId)
        }
        val initialStackCount = initialReservedStackCount(reservedTileCount)
        val track = when (trackScope) {
            ReservedWallTrackScope.SINGLE_SIDE -> SingleSideReservedWallTrackPlanner.plan(
                opening = context.wallOpening,
                stacksPerSide = stacksPerSide,
                stackCount = initialStackCount + supplementalStackCount,
                occupiedPositions = occupiedPositions,
                initialVacantStackCount = initialStackCount,
                extraStacksAfterHead = gapClearanceStacks,
            )
            ReservedWallTrackScope.CIRCULAR -> CircularReservedWallTrackPlanner.plan(
                opening = context.wallOpening,
                sideCount = sideCount,
                stacksPerSide = stacksPerSide,
                stackCount = initialStackCount + supplementalStackCount,
                occupiedPositions = occupiedPositions,
                initialVacantStackCount = initialStackCount,
                extraStacksAfterHead = gapClearanceStacks,
                alongWallStacks = reservedWallGapStacks,
            )
        } ?: return InitialPhysicalWallLayoutDecision.Rejected(noTrackReasonId)
        val reservedPlacements = createReservedPlacements(wallLayout, track)
        return InitialPhysicalWallLayoutDecision.Completed(
            layout = TileWallPhysicalLayout(
                placements = wallLayout.structure.mapValues { (tileId, position) ->
                    reservedPlacements[tileId] ?: TileWallPlacement(position)
                },
            ),
        )
    }

    /** 依規則補牌計數判斷是否需要產生保留區補位。 */
    override fun resolveTransition(context: PhysicalWallLayoutTransitionContext): PhysicalWallLayoutTransitionDecision {
        val beforeCount = completedDrawCount(context.tableStateBeforeAction)
        val afterCount = completedDrawCount(context.tableStateAfterAction)
        if (beforeCount < 0 || afterCount < 0) {
            return PhysicalWallLayoutTransitionDecision.Rejected(invalidStateReasonId)
        }
        return when {
            afterCount == beforeCount -> unchangedOrRemoveDepartedTiles(context)
            afterCount == beforeCount + 1 -> resolveSupplementalTransition(context, afterCount)
            else -> PhysicalWallLayoutTransitionDecision.Rejected(invalidTransitionReasonId)
        }
    }

    /**
     * 解析一次已知序號的保留區補牌，供規則在補牌與觸發動作分成不同步驟時共用。
     *
     * @param context 補牌前後桌況與目前布局。
     * @param drawNumber 本局完成這次補牌後的累計次數，從 1 起算。
     * @return 完成的布局與移動階段，或說明資料不一致的拒絕結果。
     */
    fun resolveSupplementalTransition(
        context: PhysicalWallLayoutTransitionContext,
        drawNumber: Int,
    ): PhysicalWallLayoutTransitionDecision {
        if (drawNumber !in 1..maximumSupplementalDrawCount) {
            return PhysicalWallLayoutTransitionDecision.Rejected(invalidTransitionReasonId)
        }
        val drawnTile = context.tableStateBeforeAction.reservedWallTiles.firstOrNull()
            ?: return PhysicalWallLayoutTransitionDecision.Rejected(invalidTransitionReasonId)
        val replenishment = context.tableStateAfterAction.reservedWallTiles.lastOrNull()
            ?: return PhysicalWallLayoutTransitionDecision.Rejected(invalidTransitionReasonId)
        val liveTilesBefore = context.tableStateBeforeAction.tileWall.getAllTiles()
        val liveTilesAfter = context.tableStateAfterAction.tileWall.getAllTiles()
        val reservedTilesBefore = context.tableStateBeforeAction.reservedWallTiles
        val reservedTilesAfter = context.tableStateAfterAction.reservedWallTiles
        if (liveTilesBefore.lastOrNull()?.id != replenishment.id ||
            liveTilesAfter.map { it.id } != liveTilesBefore.dropLast(1).map { it.id } ||
            reservedTilesAfter.map { it.id } != (reservedTilesBefore.drop(1) + replenishment).map { it.id }
        ) {
            return PhysicalWallLayoutTransitionDecision.Rejected(invalidTransitionReasonId)
        }

        val placements = context.currentLayout.placements
            .filterKeys { it != drawnTile.id }
            .toMutableMap()
        val phases = if (drawNumber % TILES_PER_STACK == 1) {
            resolveOddDrawPhases(context, drawNumber, replenishment.id, placements)
                ?: return PhysicalWallLayoutTransitionDecision.Rejected(invalidTransitionReasonId)
        } else {
            val moves = resolveEvenDrawMoves(context, drawNumber, replenishment.id, placements)
                ?: return PhysicalWallLayoutTransitionDecision.Rejected(invalidTransitionReasonId)
            listOf(PhysicalWallLayoutTransitionPhase(moves))
        }
        phases.flatMap { it.moves }.forEach { move -> placements[move.tileId] = move.destination }
        return PhysicalWallLayoutTransitionDecision.Completed(TileWallPhysicalLayout(placements), phases)
    }

    /**
     * 目前位置加上保留區之後可能用到的墩位。補牌只會把保留區往軌道尾端推，所以從目前保留區的第一墩到軌道尾端的上下兩層，
     * 涵蓋這一局剩下所有補牌後保留牌的位置。
     */
    override fun roundReach(tableState: TableState): Set<TileWallPlacement> {
        val placements = tableState.physicalWallLayout?.placements ?: return emptySet()
        val current = placements.values.toSet()
        val drawCount = completedDrawCount(tableState)
        val frontPlacement = tableState.reservedWallTiles.firstOrNull()?.let { placements[it.id] }
        if (drawCount !in 0..maximumSupplementalDrawCount || frontPlacement == null) return current
        val frontTrackIndex = reservedFrontTrackIndex(drawCount)
        val totalStacks = sideCount * stacksPerSide
        val frontStack = frontPlacement.position.side * stacksPerSide + frontPlacement.position.stack
        val trackHeadStack = floorMod(frontStack + frontTrackIndex, totalStacks)
        val trackEnd = initialReservedStackCount(tableState.config.reservedTileCount) + supplementalStackCount
        val future = (frontTrackIndex until trackEnd).flatMap { trackIndex ->
            val globalStack = floorMod(trackHeadStack - trackIndex, totalStacks)
            (0 until TILES_PER_STACK).map { layer ->
                TileWallPlacement(TileWallPosition(globalStack / stacksPerSide, globalStack % stacksPerSide, layer), reservedWallGapOffset)
            }
        }
        return current + future
    }

    /**
     * 完成指定次數補牌後，保留區第一張牌所在的軌道墩位。
     *
     * @param drawCount 本局已完成的補牌次數。
     * @return 從軌道頭端起算的墩位。
     */
    private fun reservedFrontTrackIndex(drawCount: Int): Int = when (initialShape) {
        ReservedWallInitialShape.STACKED -> drawCount / TILES_PER_STACK
        ReservedWallInitialShape.SPLIT_FIRST_TWO -> when (drawCount) {
            0 -> 0
            1 -> 1
            else -> drawCount / TILES_PER_STACK + 1
        }
    }

    /** 依規則指定的靠缺口形狀建立保留牌位置。 */
    private fun createReservedPlacements(
        wallLayout: TileWallLayoutResult,
        track: ReservedWallTrack,
    ): Map<Uuid, TileWallPlacement> = buildMap {
        wallLayout.reservedWallTiles.forEachIndexed { index, tile ->
            val position = when (initialShape) {
                ReservedWallInitialShape.STACKED -> track.position(index / TILES_PER_STACK, if (index % TILES_PER_STACK == 0) 1 else 0)
                ReservedWallInitialShape.SPLIT_FIRST_TWO -> when (index) {
                    0 -> track.position(0, 0)
                    1 -> track.position(1, 0)
                    else -> track.position(index / TILES_PER_STACK + 1, if (index % TILES_PER_STACK == 0) 1 else 0)
                }
            }
            put(tile.id, TileWallPlacement(position, reservedWallGapOffset))
        }
    }

    /** 奇數次補牌先移入下層補充牌，再讓同墩上層牌下降。 */
    private fun resolveOddDrawPhases(
        context: PhysicalWallLayoutTransitionContext,
        drawNumber: Int,
        replenishmentId: Uuid,
        placements: Map<Uuid, TileWallPlacement>,
    ): List<PhysicalWallLayoutTransitionPhase>? {
        val loweredTile = context.tableStateAfterAction.tileWall.getAllTiles().lastOrNull() ?: return null
        val replenishmentPlacement = placements[replenishmentId] ?: return null
        val loweredPlacement = placements[loweredTile.id] ?: return null
        if (replenishmentPlacement.position.layer != 0 || loweredPlacement.position.layer != 1) return null
        val replenishmentDestination = replenishmentDestination(context, drawNumber, layer = 0) ?: return null
        return listOf(
            PhysicalWallLayoutTransitionPhase(
                moves = listOf(PhysicalWallTileMove(replenishmentId, replenishmentPlacement, replenishmentDestination)),
            ),
            PhysicalWallLayoutTransitionPhase(
                moves = listOf(
                    element = PhysicalWallTileMove(
                        tileId = loweredTile.id,
                        source = loweredPlacement,
                        destination = TileWallPlacement(loweredPlacement.position.copy(layer = 0)),
                    ),
                ),
            ),
        )
    }

    /** 偶數次補牌把前次下降的牌移入保留區上層。 */
    private fun resolveEvenDrawMoves(
        context: PhysicalWallLayoutTransitionContext,
        drawNumber: Int,
        replenishmentId: Uuid,
        placements: Map<Uuid, TileWallPlacement>,
    ): List<PhysicalWallTileMove>? {
        val currentPlacement = placements[replenishmentId] ?: return null
        if (currentPlacement.position.layer != 0 || currentPlacement.offset != TileWallPlacementOffset.Zero) return null
        val destination = replenishmentDestination(context, drawNumber, layer = 1) ?: return null
        return listOf(PhysicalWallTileMove(replenishmentId, currentPlacement, destination))
    }

    /** 取得這次由一般摸牌區補入保留區尾端的位置。 */
    private fun replenishmentDestination(
        context: PhysicalWallLayoutTransitionContext,
        drawNumber: Int,
        layer: Int,
    ): TileWallPlacement? {
        val nextReservedId = context.tableStateAfterAction.reservedWallTiles.firstOrNull()?.id ?: return null
        val nextReservedPlacement = context.currentLayout.placements[nextReservedId] ?: return null
        val nextReservedTrackIndex = reservedFrontTrackIndex(drawNumber)
        val nextReservedGlobalStack = nextReservedPlacement.position.side * stacksPerSide + nextReservedPlacement.position.stack
        val trackIndex = initialReservedStackCount(context.tableStateBeforeAction.config.reservedTileCount) +
            (drawNumber - 1) / TILES_PER_STACK
        val totalStacks = sideCount * stacksPerSide
        val trackHeadStack = floorMod(nextReservedGlobalStack + nextReservedTrackIndex, totalStacks)
        val globalStack = floorMod(trackHeadStack - trackIndex, totalStacks)
        return TileWallPlacement(
            position = TileWallPosition(globalStack / stacksPerSide, globalStack % stacksPerSide, layer),
            offset = reservedWallGapOffset,
        )
    }

    /** 不需要補牌的動作只移除已離開牌牆的牌。 */
    private fun unchangedOrRemoveDepartedTiles(
        context: PhysicalWallLayoutTransitionContext,
    ): PhysicalWallLayoutTransitionDecision {
        val expectedIds = (context.tableStateAfterAction.tileWall.getAllTiles() + context.tableStateAfterAction.reservedWallTiles)
            .mapTo(mutableSetOf()) { it.id }
        if (!context.currentLayout.placements.keys.containsAll(expectedIds)) {
            return PhysicalWallLayoutTransitionDecision.Rejected(invalidTransitionReasonId)
        }
        if (context.currentLayout.placements.keys == expectedIds) return PhysicalWallLayoutTransitionDecision.Unchanged
        return PhysicalWallLayoutTransitionDecision.Completed(
            layout = TileWallPhysicalLayout(context.currentLayout.placements.filterKeys { it in expectedIds }),
            phases = emptyList(),
        )
    }

    /** 初始保留牌實際占用的墩數。 */
    private fun initialReservedStackCount(reservedTileCount: Int): Int = reservedTileCount / TILES_PER_STACK +
        if (initialShape == ReservedWallInitialShape.SPLIT_FIRST_TWO) 1 else 0

    /** 後續補牌最多需要增加的墩數。 */
    private val supplementalStackCount: Int
        get() = ceil(maximumSupplementalDrawCount.toDouble() / TILES_PER_STACK).toInt()

    /** 平移後軌道頭端必須額外保持淨空的墩數。 */
    private val gapClearanceStacks: Int
        get() = ceil(reservedWallGapStacks).toInt()

    /** 形成可見分界的保留區位移。 */
    private val reservedWallGapOffset: TileWallPlacementOffset
        get() = TileWallPlacementOffset(alongWallStacks = reservedWallGapStacks)

    /** 從完整且對稱的結構取得每面墩數。 */
    private fun TileWallLayoutResult.stacksPerSideOrNull(): Int? {
        val counts = structure.values.groupBy { it.side }.values.map { positions -> positions.maxOf { it.stack } + 1 }
        return counts.distinct().singleOrNull()
    }

    /** 完整牌牆的面數。 */
    private val TileWallLayoutResult.sideCount: Int
        get() = structure.values.maxOf { it.side } + 1

    /** 恆為非負的餘數。 */
    private fun floorMod(value: Int, modulus: Int): Int = ((value % modulus) + modulus) % modulus

    private companion object {
        /** 每墩牌數。 */
        const val TILES_PER_STACK = 2
    }
}
