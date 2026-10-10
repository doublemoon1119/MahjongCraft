package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallTileMove
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import kotlin.uuid.Uuid

/** 將規則 transition phases 轉成同步且碰撞安全的世界路徑。 */
object TileWallTransitionMotionPlanner {
    /** 將砌牆格位到規則最終布局建立為單一同步開門 phase。 */
    fun planOpening(
        context: MahjongTileWallProjectionContext,
        assemblyStructure: Map<Uuid, TileWallPosition>,
        finalLayout: TileWallPhysicalLayout,
    ): TileWallTransitionMotionDecision {
        if (assemblyStructure.keys != finalLayout.placements.keys) {
            return TileWallTransitionMotionDecision.Rejected(
                assemblyStructure.keys.firstOrNull() ?: finalLayout.placements.keys.first(),
                TileWallMotionPathRejection.INCONSISTENT_LAYOUT,
            )
        }
        val moves = assemblyStructure.mapNotNull { (tileId, position) ->
            val source = TileWallPlacement(position)
            val destination = finalLayout.placements.getValue(tileId)
            if (source == destination) null else PhysicalWallTileMove(tileId, source, destination)
        }
        if (moves.isEmpty()) return TileWallTransitionMotionDecision.Completed(TileWallTransitionMotionPlan(emptyList()))
        return plan(context, listOf(PhysicalWallLayoutTransitionPhase(moves)))
    }

    /** 依 [context] 規劃所有 [phases]，任一移動失敗時不回傳部分結果。 */
    fun plan(
        context: MahjongTileWallProjectionContext,
        phases: List<PhysicalWallLayoutTransitionPhase>,
    ): TileWallTransitionMotionDecision {
        val plannedPhases = mutableListOf<TileWallMotionPhasePlan>()
        phases.forEach { phase ->
            val motions = mutableListOf<TileWallPhaseMotion>()
            phase.moves.forEach { move ->
                when (val decision = planMove(context, move)) {
                    is TileWallMotionPathDecision.Completed -> motions += TileWallPhaseMotion(move.tileId, decision.segments)
                    is TileWallMotionPathDecision.Rejected -> {
                        return TileWallTransitionMotionDecision.Rejected(move.tileId, decision.reason)
                    }
                }
            }
            plannedPhases += TileWallMotionPhasePlan(motions)
        }
        return TileWallTransitionMotionDecision.Completed(TileWallTransitionMotionPlan(plannedPhases))
    }

    /** 將同時包含水平與升降的 move 拆成不穿過同墩牌的兩段 placement 路徑。 */
    private fun planMove(
        context: MahjongTileWallProjectionContext,
        move: PhysicalWallTileMove,
    ): TileWallMotionPathDecision {
        val direct = TileWallMotionPathPlanner.plan(context, move.source, move.destination)
        if (direct !is TileWallMotionPathDecision.Rejected ||
            direct.reason != TileWallMotionPathRejection.MIXED_HORIZONTAL_AND_VERTICAL
        ) {
            return direct
        }
        val sourceWorld = context.project(move.source)
        val destinationWorld = context.project(move.destination)
        val intermediate = if (destinationWorld.y > sourceWorld.y) {
            move.source.copy(position = move.source.position.copy(layer = move.destination.position.layer))
        } else {
            move.destination.copy(position = move.destination.position.copy(layer = move.source.position.layer))
        }
        val first = TileWallMotionPathPlanner.plan(context, move.source, intermediate)
        val second = TileWallMotionPathPlanner.plan(context, intermediate, move.destination)
        return if (first is TileWallMotionPathDecision.Completed && second is TileWallMotionPathDecision.Completed) {
            TileWallMotionPathDecision.Completed(first.segments + second.segments)
        } else {
            first.takeIf { it is TileWallMotionPathDecision.Rejected } ?: second
        }
    }
}
