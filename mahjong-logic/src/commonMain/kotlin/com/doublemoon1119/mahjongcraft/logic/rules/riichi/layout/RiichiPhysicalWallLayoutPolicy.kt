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
        val firstRinshanTile = wallLayout.reservedWallTiles.firstOrNull()
            ?: return InitialPhysicalWallLayoutDecision.Rejected(INVALID_STATE_REASON_ID)
        val firstRinshanPosition = wallLayout.structure[firstRinshanTile.id]
            ?: return InitialPhysicalWallLayoutDecision.Rejected(INVALID_STATE_REASON_ID)
        if (wallLayout.reservedWallTiles.size < RiichiSupplementalDrawPolicy.MAX_SUPPLEMENTAL_DRAWS) {
            return InitialPhysicalWallLayoutDecision.Rejected(INVALID_STATE_REASON_ID)
        }

        val reservedIds = wallLayout.reservedWallTiles.mapTo(mutableSetOf()) { it.id }
        val placements = wallLayout.structure.mapValues { (tileId, position) ->
            when {
                tileId == firstRinshanTile.id -> TileWallPlacement(
                    position = firstRinshanPosition.nextStack(),
                    offset = DEAD_WALL_OFFSET,
                )

                tileId in reservedIds -> TileWallPlacement(position, DEAD_WALL_OFFSET)
                else -> TileWallPlacement(position)
            }
        }
        return InitialPhysicalWallLayoutDecision.Completed(TileWallPhysicalLayout(placements))
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
            resolveOddDrawPhases(context, replenishment.id, remainingPlacements)
                ?: return PhysicalWallLayoutTransitionDecision.Rejected(INVALID_TRANSITION_REASON_ID)
        } else {
            val moves = resolveEvenDrawMoves(replenishment.id, remainingPlacements)
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
        replenishmentId: Uuid,
        placements: Map<Uuid, TileWallPlacement>,
    ): List<PhysicalWallLayoutTransitionPhase>? {
        val loweredTile = context.tableStateAfterAction.tileWall.getAllTiles().lastOrNull() ?: return null
        val replenishmentPlacement = placements[replenishmentId] ?: return null
        val loweredPlacement = placements[loweredTile.id] ?: return null
        if (replenishmentPlacement.position.layer != 0 || loweredPlacement.position.layer != 1) return null
        val loweredDestination = TileWallPlacement(loweredPlacement.position.copy(layer = 0))
        val replenishmentDestination = TileWallPlacement(replenishmentPlacement.position, DEAD_WALL_OFFSET)
        return listOf(
            PhysicalWallLayoutTransitionPhase(listOf(PhysicalWallTileMove(replenishmentId, replenishmentDestination))),
            PhysicalWallLayoutTransitionPhase(listOf(PhysicalWallTileMove(loweredTile.id, loweredDestination))),
        )
    }

    /** 偶數次補牌時，把前次已下降的上層活牌移入王牌區並疊在前一張補充牌上。 */
    private fun resolveEvenDrawMoves(
        replenishmentId: Uuid,
        placements: Map<Uuid, TileWallPlacement>,
    ): List<PhysicalWallTileMove>? {
        val currentPlacement = placements[replenishmentId] ?: return null
        if (currentPlacement.position.layer != 0 || currentPlacement.offset != TileWallPlacementOffset.Zero) return null
        return listOf(
            PhysicalWallTileMove(
                replenishmentId,
                TileWallPlacement(currentPlacement.position.copy(layer = 1), DEAD_WALL_OFFSET),
            ),
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

    /** 取得沿牌牆一般摸牌方向相鄰的下一墩，跨面時從下一面的最右墩接續。 */
    private fun TileWallPosition.nextStack(): TileWallPosition {
        val globalStack = side * STACKS_PER_SIDE + stack
        val nextGlobalStack = (globalStack + 1) % TOTAL_STACKS
        return TileWallPosition(nextGlobalStack / STACKS_PER_SIDE, nextGlobalStack % STACKS_PER_SIDE, layer = 0)
    }

    /** 日麻牌牆每一面的固定墩數。 */
    private const val STACKS_PER_SIDE = 17

    /** 日麻完整牌牆的固定總墩數。 */
    private const val TOTAL_STACKS = STACKS_PER_SIDE * 4

    /** 將王牌區沿牌牆朝開門空位平移四分之一墩所使用的抽象位移。 */
    private val DEAD_WALL_OFFSET = TileWallPlacementOffset(alongWallStacks = 0.25)

    /** 初始牌牆缺少日麻嶺上牌結構時的拒絕原因。 */
    const val INVALID_STATE_REASON_ID: String = "mahjongcraft:invalid_riichi_physical_wall_state"

    /** 槓前後牌牆或補牌次數不符合日麻 transition 契約時的拒絕原因。 */
    const val INVALID_TRANSITION_REASON_ID: String = "mahjongcraft:invalid_riichi_physical_wall_transition"
}
