package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallTileMove
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import kotlin.uuid.Uuid

/** 單張牌在一個規則 phase 中依序播放的幾何路徑。 */
data class TileWallPhaseMotion(
    /** 要移動的既有牌張 Uuid。 */
    val tileId: Uuid,
    /** 已按碰撞安全順序排列的路徑段。 */
    val segments: List<TileWallMotionSegment>,
) {
    init {
        require(segments.isNotEmpty()) { "Wall phase motion must contain at least one segment" }
    }

    /** 此牌完成本 phase 所需的 ticks。 */
    val durationTicks: Int = segments.sumOf { it.durationTicks }
}

/** 一個共用時間邊界內同步開始的牌牆移動階段。 */
data class TileWallMotionPhasePlan(
    /** 此階段內各張牌的路徑。 */
    val motions: List<TileWallPhaseMotion>,
) {
    init {
        require(motions.isNotEmpty()) { "Wall motion phase plan must contain at least one motion" }
    }

    /** 下一個 phase 最早可開始的相對 ticks。 */
    val durationTicks: Int = motions.maxOf { it.durationTicks }
}

/** 完整牌牆 transition 的確定性幾何與 phase barrier 計畫。 */
data class TileWallTransitionMotionPlan(
    /** 依權威順序排列的移動階段。 */
    val phases: List<TileWallMotionPhasePlan>,
) {
    /** 全部階段播放完成所需的 ticks。 */
    val durationTicks: Int = phases.sumOf { it.durationTicks }
}

/** 牌牆 transition 路徑規劃結果。 */
sealed interface TileWallTransitionMotionDecision {
    /** 所有移動均已成功規劃。 */
    data class Completed(val plan: TileWallTransitionMotionPlan) : TileWallTransitionMotionDecision

    /** 至少一個移動無法轉成安全路徑。 */
    data class Rejected(val tileId: Uuid, val reason: TileWallMotionPathRejection) : TileWallTransitionMotionDecision
}

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
