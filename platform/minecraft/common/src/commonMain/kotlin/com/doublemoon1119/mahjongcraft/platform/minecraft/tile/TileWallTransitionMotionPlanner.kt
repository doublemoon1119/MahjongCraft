package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallTileMove
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import kotlin.uuid.Uuid

/** 將規則 transition phases 轉成同步且碰撞安全的世界路徑。 */
object TileWallTransitionMotionPlanner {
    /**
     * 將砌牆格位到規則最終布局建立為單一同步開門 phase。
     *
     * @param context 此桌的幾何投影資料。
     * @param assemblyStructure 開門前各牌的組裝格位。
     * @param finalLayout 規則指定的開門後布局。
     * @return 全部移動的同步路徑，或無法安全規劃的牌與原因。
     */
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

    /**
     * 依 [context] 規劃所有 [phases]，任一移動失敗時不回傳部分結果。
     *
     * @param context 此桌的幾何投影資料。
     * @param phases 依規則順序排列的移動階段。
     * @return 完整移動計畫，或第一個無法安全規劃的牌與原因。
     */
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

    /**
     * 將同時包含水平與升降的 move 拆成不穿過同墩牌的兩段 placement 路徑。
     *
     * @param context 此桌的幾何投影資料。
     * @param move 要規劃的牌移動。
     * @return 拆分後的成功路徑，或幾何規劃拒絕的原因。
     */
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
