package com.doublemoon1119.mahjongcraft.logic.rules.riichi.layout

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiSupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.InitialPhysicalWallLayoutDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutPolicy
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionDecision
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallTileMove
import com.doublemoon1119.mahjongcraft.logic.table.layout.SingleSideReservedWallTrack
import com.doublemoon1119.mahjongcraft.logic.table.layout.SingleSideReservedWallTrackPlanner
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayoutResult
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacementOffset
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import kotlin.uuid.Uuid

/** 日本麻將的獨立王牌區、特殊嶺上牌位置與槓後補位布局。 */
object RiichiPhysicalWallLayoutPolicy : PhysicalWallLayoutPolicy {
    /** 建立帶王牌分界，並將第一張嶺上牌預先放到缺口外側下層的開局布局。 */
    override fun createInitialLayout(context: InitialPhysicalWallLayoutContext): InitialPhysicalWallLayoutDecision {
        val wallLayout = context.wallLayout
        if (wallLayout.reservedWallTiles.size != RESERVED_TILE_COUNT) {
            return InitialPhysicalWallLayoutDecision.Rejected(INVALID_STATE_REASON_ID)
        }
        val reservedIds = wallLayout.reservedWallTiles.mapTo(mutableSetOf()) { it.id }
        val occupiedPositions = context.occupiedTileIds
            .asSequence()
            .filter { tileId -> tileId !in reservedIds }
            .mapNotNull(wallLayout.structure::get)
            .toSet()
        val stacksPerSide = wallLayout.stacksPerSideOrNull()
            ?: return InitialPhysicalWallLayoutDecision.Rejected(INVALID_STATE_REASON_ID)
        val track = SingleSideReservedWallTrackPlanner.plan(
            opening = context.wallOpening,
            stacksPerSide = stacksPerSide,
            stackCount = RESERVED_TRACK_STACK_COUNT,
            occupiedPositions = occupiedPositions,
            extraStackAfterHead = true,
        ) ?: return InitialPhysicalWallLayoutDecision.Rejected(NO_COLLISION_FREE_TRACK_REASON_ID)
        val reservedPlacements = createReservedPlacements(wallLayout, track)
        val placements = wallLayout.structure.mapValues { (tileId, position) ->
            reservedPlacements[tileId] ?: TileWallPlacement(position)
        }
        return InitialPhysicalWallLayoutDecision.Completed(TileWallPhysicalLayout(placements))
    }

    /** 將 14 張日麻王牌依「嶺 1、嶺 2、其餘完整牌墩」投影到開門面的連續軌道。 */
    private fun createReservedPlacements(
        wallLayout: TileWallLayoutResult,
        track: SingleSideReservedWallTrack,
    ): Map<Uuid, TileWallPlacement> = buildMap {
        wallLayout.reservedWallTiles.forEachIndexed { index, tile ->
            val position = when (index) {
                0 -> track.position(0, 0)
                1 -> track.position(1, 0)
                else -> track.position(index / 2 + 1, if (index % 2 == 0) 1 else 0)
            }
            put(tile.id, TileWallPlacement(position, RESERVED_WALL_GAP_OFFSET))
        }
    }

    /**
     * 依補牌完成次數重排活牌末端：奇數次先形成半墩，偶數次再把剩餘牌疊回完整一墩。
     */
    override fun resolveTransition(context: PhysicalWallLayoutTransitionContext): PhysicalWallLayoutTransitionDecision {
        if (context.action !is GameAction.Kan) return unchangedOrRemoveDepartedTiles(context)
        val beforeState = context.tableStateBeforeAction.dynamicRuleState as? RiichiDynamicState
            ?: return PhysicalWallLayoutTransitionDecision.Rejected(INVALID_STATE_REASON_ID)
        val afterState = context.tableStateAfterAction.dynamicRuleState as? RiichiDynamicState
            ?: return PhysicalWallLayoutTransitionDecision.Rejected(INVALID_STATE_REASON_ID)
        val drawNumber = afterState.completedSupplementalDrawCount
        if (drawNumber != beforeState.completedSupplementalDrawCount + 1 ||
            drawNumber !in 1..RiichiSupplementalDrawPolicy.MAX_SUPPLEMENTAL_DRAWS
        ) {
            return PhysicalWallLayoutTransitionDecision.Rejected(INVALID_TRANSITION_REASON_ID)
        }

        val drawnTile = context.tableStateBeforeAction.reservedWallTiles.firstOrNull()
            ?: return PhysicalWallLayoutTransitionDecision.Rejected(INVALID_TRANSITION_REASON_ID)
        val replenishment = context.tableStateAfterAction.reservedWallTiles.lastOrNull()
            ?: return PhysicalWallLayoutTransitionDecision.Rejected(INVALID_TRANSITION_REASON_ID)
        val liveTilesBefore = context.tableStateBeforeAction.tileWall.getAllTiles()
        val liveTilesAfter = context.tableStateAfterAction.tileWall.getAllTiles()
        val reservedTilesBefore = context.tableStateBeforeAction.reservedWallTiles
        val reservedTilesAfter = context.tableStateAfterAction.reservedWallTiles
        if (liveTilesBefore.lastOrNull()?.id != replenishment.id ||
            liveTilesAfter.map { it.id } != liveTilesBefore.dropLast(1).map { it.id } ||
            reservedTilesAfter.map { it.id } != (reservedTilesBefore.drop(1) + replenishment).map { it.id }
        ) {
            return PhysicalWallLayoutTransitionDecision.Rejected(INVALID_TRANSITION_REASON_ID)
        }

        val remainingPlacements = context.currentLayout.placements
            .filterKeys { it != drawnTile.id }
            .toMutableMap()
        val phases = if (drawNumber % 2 == 1) {
            resolveOddDrawPhases(context, drawNumber, replenishment.id, remainingPlacements)
                ?: return PhysicalWallLayoutTransitionDecision.Rejected(INVALID_TRANSITION_REASON_ID)
        } else {
            val moves = resolveEvenDrawMoves(context, drawNumber, replenishment.id, remainingPlacements)
                ?: return PhysicalWallLayoutTransitionDecision.Rejected(INVALID_TRANSITION_REASON_ID)
            listOf(PhysicalWallLayoutTransitionPhase(moves))
        }
        phases.flatMap { it.moves }.forEach { remainingPlacements[it.tileId] = it.destination }
        return PhysicalWallLayoutTransitionDecision.Completed(
            layout = TileWallPhysicalLayout(remainingPlacements),
            phases = phases,
        )
    }

    /** 奇數次補牌時，先讓下層補充牌滑入王牌區，再讓同墩上層活牌下降。 */
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
        val loweredDestination = TileWallPlacement(loweredPlacement.position.copy(layer = 0))
        val replenishmentDestination = replenishmentDestination(context, drawNumber, layer = 0) ?: return null
        return listOf(
            PhysicalWallLayoutTransitionPhase(
                listOf(PhysicalWallTileMove(replenishmentId, replenishmentPlacement, replenishmentDestination)),
            ),
            PhysicalWallLayoutTransitionPhase(
                listOf(PhysicalWallTileMove(loweredTile.id, loweredPlacement, loweredDestination)),
            ),
        )
    }

    /** 偶數次補牌時，把前次已下降的上層活牌移入王牌區並疊在前一張補充牌上。 */
    private fun resolveEvenDrawMoves(
        context: PhysicalWallLayoutTransitionContext,
        drawNumber: Int,
        replenishmentId: Uuid,
        placements: Map<Uuid, TileWallPlacement>,
    ): List<PhysicalWallTileMove>? {
        val currentPlacement = placements[replenishmentId] ?: return null
        if (currentPlacement.position.layer != 0 || currentPlacement.offset != TileWallPlacementOffset.Zero) return null
        val destination = replenishmentDestination(context, drawNumber, layer = 1) ?: return null
        return listOf(
            PhysicalWallTileMove(
                replenishmentId,
                currentPlacement,
                destination,
            ),
        )
    }

    /** 依補牌序號取得開門面軌道尾端的補入位置；每兩次補牌共用同一墩。 */
    private fun replenishmentDestination(
        context: PhysicalWallLayoutTransitionContext,
        drawNumber: Int,
        layer: Int,
    ): TileWallPlacement? {
        val nextReservedId = context.tableStateAfterAction.reservedWallTiles.firstOrNull()?.id ?: return null
        val nextReservedPlacement = context.currentLayout.placements[nextReservedId] ?: return null
        val nextReservedTrackIndex = when (drawNumber) {
            1 -> 1
            else -> drawNumber / 2 + 1
        }
        val trackHeadStack = nextReservedPlacement.position.stack + nextReservedTrackIndex
        val trackIndex = FIRST_REPLENISHMENT_TRACK_INDEX + (drawNumber - 1) / 2
        val stack = trackHeadStack - trackIndex
        if (stack < 0) return null
        return TileWallPlacement(
            TileWallPosition(nextReservedPlacement.position.side, stack, layer),
            RESERVED_WALL_GAP_OFFSET,
        )
    }

    /** 非槓動作只移除已離開牌牆的牌，不建立日麻補位動畫。 */
    private fun unchangedOrRemoveDepartedTiles(
        context: PhysicalWallLayoutTransitionContext,
    ): PhysicalWallLayoutTransitionDecision {
        val expectedIds = (
            context.tableStateAfterAction.tileWall.getAllTiles() +
                context.tableStateAfterAction.reservedWallTiles
            ).mapTo(mutableSetOf()) { it.id }
        if (!context.currentLayout.placements.keys.containsAll(expectedIds)) {
            return PhysicalWallLayoutTransitionDecision.Rejected(INVALID_TRANSITION_REASON_ID)
        }
        if (context.currentLayout.placements.keys == expectedIds) return PhysicalWallLayoutTransitionDecision.Unchanged
        return PhysicalWallLayoutTransitionDecision.Completed(
            TileWallPhysicalLayout(context.currentLayout.placements.filterKeys { it in expectedIds }),
            emptyList(),
        )
    }

    /** 從完整四面結構推導每一面的墩數，並拒絕不對稱或空布局。 */
    private fun TileWallLayoutResult.stacksPerSideOrNull(): Int? {
        val counts = structure.values.groupBy { it.side }.values.map { positions -> positions.maxOf { it.stack } + 1 }
        return counts.distinct().singleOrNull()
    }

    /** 日本麻將固定王牌張數。 */
    private const val RESERVED_TILE_COUNT = 14

    /** 初始 14 張王牌的八格布局，加上兩墩供四次槓補入牌使用。 */
    private const val RESERVED_TRACK_STACK_COUNT = 10

    /** 第一次補入牌位於軌道的第八格，之後每兩次補牌向尾端推進一墩。 */
    private const val FIRST_REPLENISHMENT_TRACK_INDEX = 8

    /** 將集中後的保留牌整體朝開門空位平移四分之一墩，形成清楚分界。 */
    private val RESERVED_WALL_GAP_OFFSET = TileWallPlacementOffset(alongWallStacks = 0.25)

    /** 初始牌牆缺少日麻嶺上牌結構時的拒絕原因。 */
    const val INVALID_STATE_REASON_ID: String = "mahjongcraft:invalid_riichi_physical_wall_state"

    /** 開門面沒有足夠連續且無占用的保留牌軌道時的拒絕原因。 */
    const val NO_COLLISION_FREE_TRACK_REASON_ID: String = "mahjongcraft:no_collision_free_reserved_wall_track"

    /** 槓前後牌牆或補牌次數不符合日麻 transition 契約時的拒絕原因。 */
    const val INVALID_TRANSITION_REASON_ID: String = "mahjongcraft:invalid_riichi_physical_wall_transition"
}
