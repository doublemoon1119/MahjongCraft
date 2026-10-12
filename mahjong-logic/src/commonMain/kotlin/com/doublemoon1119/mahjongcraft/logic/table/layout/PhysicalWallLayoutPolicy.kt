package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 規則提供的抽象實體牌牆布局能力。
 *
 * 規則只描述正規化位置與移動階段；具體座標、碰撞安全路徑及移動方式由使用此結果的外層自行決定。
 */
interface PhysicalWallLayoutPolicy {
    /** 建立一局牌牆的初始實體布局。 */
    fun createInitialLayout(context: InitialPhysicalWallLayoutContext): InitialPhysicalWallLayoutDecision

    /** 根據已完成的遊戲動作與牌牆變化解析新的實體布局。 */
    fun resolveTransition(context: PhysicalWallLayoutTransitionContext): PhysicalWallLayoutTransitionDecision
}

/** 建立初始實體牌牆布局的強型別結果。 */
sealed interface InitialPhysicalWallLayoutDecision {
    /**
     * 初始布局已成功建立。
     *
     * @property layout 全部初始牌牆牌張的抽象實體位置。
     */
    data class Completed(val layout: TileWallPhysicalLayout) : InitialPhysicalWallLayoutDecision

    /**
     * 規則拒絕建立初始布局。
     *
     * @property reasonId 完整 namespaced 拒絕原因 ID。
     */
    data class Rejected(val reasonId: String) : InitialPhysicalWallLayoutDecision {
        init {
            requirePhysicalWallReasonId(reasonId)
        }
    }
}

/** 動作完成後的實體牌牆布局決策。 */
sealed interface PhysicalWallLayoutTransitionDecision {
    /** 牌牆成員與位置皆未改變。 */
    data object Unchanged : PhysicalWallLayoutTransitionDecision

    /**
     * 新布局與其宣告式移動階段已成功解析。
     *
     * @property layout 所有移動階段完成後的唯一權威最終布局。
     * @property phases 依序播放的移動階段；只移除牌牆成員而沒有位置移動時可以為空。
     */
    data class Completed(
        val layout: TileWallPhysicalLayout,
        val phases: List<PhysicalWallLayoutTransitionPhase>,
    ) : PhysicalWallLayoutTransitionDecision

    /**
     * 規則拒絕此次布局演進。
     *
     * @property reasonId 完整 namespaced 拒絕原因 ID。
     */
    data class Rejected(val reasonId: String) : PhysicalWallLayoutTransitionDecision {
        init {
            requirePhysicalWallReasonId(reasonId)
        }
    }
}

/**
 * 保持 [TileWallLayoutResult.structure] 基本格位、不建立特殊分界或移位的預設 policy。
 *
 * 動作後只移除已離開牌牆的牌，不自行移動仍留在牌牆中的牌。
 */
object ContinuousPhysicalWallLayoutPolicy : PhysicalWallLayoutPolicy {
    /** 將既有拓樸格位直接轉成零位移 placement。 */
    override fun createInitialLayout(context: InitialPhysicalWallLayoutContext): InitialPhysicalWallLayoutDecision = InitialPhysicalWallLayoutDecision.Completed(
        TileWallPhysicalLayout(
            context.wallLayout.structure.mapValues { (_, position) -> TileWallPlacement(position) },
        ),
    )

    /** 保留仍位於牌牆中的 placement，並移除已被摸走的牌張。 */
    override fun resolveTransition(context: PhysicalWallLayoutTransitionContext): PhysicalWallLayoutTransitionDecision {
        val expectedTileIds = context.tableStateAfterAction.physicalWallTileIds()
        if (!context.currentLayout.placements.keys.containsAll(expectedTileIds)) {
            return PhysicalWallLayoutTransitionDecision.Rejected(PhysicalWallLayoutReasonIds.INVALID_RESULT)
        }
        if (context.currentLayout.placements.keys == expectedTileIds) {
            return PhysicalWallLayoutTransitionDecision.Unchanged
        }
        return PhysicalWallLayoutTransitionDecision.Completed(
            layout = TileWallPhysicalLayout(context.currentLayout.placements.filterKeys { it in expectedTileIds }),
            phases = emptyList(),
        )
    }
}

/**
 * 建立並驗證初始實體牌牆布局。
 *
 * @param context 建立布局所需的牌牆、開門與占位牌資料。
 * @return policy 的原始決策；若完成布局未涵蓋完整初始牌牆，回傳無效結果拒絕決策。
 */
fun PhysicalWallLayoutPolicy.createInitialLayoutValidated(
    context: InitialPhysicalWallLayoutContext,
): InitialPhysicalWallLayoutDecision {
    val decision = createInitialLayout(context)
    if (decision !is InitialPhysicalWallLayoutDecision.Completed) return decision
    return if (decision.layout.placements.keys == context.wallLayout.structure.keys) {
        decision
    } else {
        InitialPhysicalWallLayoutDecision.Rejected(PhysicalWallLayoutReasonIds.INVALID_RESULT)
    }
}

/**
 * 解析並驗證動作完成後的實體牌牆布局演進。
 *
 * @param context 動作前後桌況與目前布局。
 * @return policy 的決策；若結果未通過成員、位置或移動階段驗證，回傳無效結果拒絕決策。
 */
fun PhysicalWallLayoutPolicy.resolveTransitionValidated(
    context: PhysicalWallLayoutTransitionContext,
): PhysicalWallLayoutTransitionDecision {
    val decision = resolveTransition(context)
    val expectedTileIds = context.tableStateAfterAction.physicalWallTileIds()
    return when (decision) {
        PhysicalWallLayoutTransitionDecision.Unchanged -> {
            if (context.currentLayout.placements.keys == expectedTileIds) {
                decision
            } else {
                PhysicalWallLayoutTransitionDecision.Rejected(PhysicalWallLayoutReasonIds.INVALID_RESULT)
            }
        }

        is PhysicalWallLayoutTransitionDecision.Rejected -> decision
        is PhysicalWallLayoutTransitionDecision.Completed -> decision.takeIf {
            it.isValidFor(context.currentLayout, expectedTileIds)
        } ?: PhysicalWallLayoutTransitionDecision.Rejected(PhysicalWallLayoutReasonIds.INVALID_RESULT)
    }
}

/**
 * 此完成結果是否完整描述 [expectedTileIds] 的最終位置及所有實際位置變化。
 *
 * @param currentLayout 動作前的牌張位置。
 * @param expectedTileIds 動作後仍應位於牌牆中的牌張 ID。
 * @return 完成布局及移動階段完全符合成員與位置驗證時為 true。
 */
private fun PhysicalWallLayoutTransitionDecision.Completed.isValidFor(
    currentLayout: TileWallPhysicalLayout,
    expectedTileIds: Set<Uuid>,
): Boolean {
    if (layout.placements.keys != expectedTileIds) return false
    val workingPlacements = currentLayout.placements
        .filterKeys { it in expectedTileIds }
        .toMutableMap()
    if (workingPlacements.keys != expectedTileIds) return false
    phases.forEach { phase ->
        if (phase.moves.any { move ->
                move.tileId !in expectedTileIds || workingPlacements[move.tileId] != move.source
            }
        ) {
            return false
        }
        val movedTileIds = phase.moves.mapTo(mutableSetOf()) { it.tileId }
        val unchangedDestinations = workingPlacements
            .filterKeys { it !in movedTileIds }
            .values
        val phaseDestinations = phase.moves.map { it.destination }
        if ((unchangedDestinations + phaseDestinations).distinct().size != expectedTileIds.size) return false
        phase.moves.forEach { move -> workingPlacements[move.tileId] = move.destination }
    }
    return workingPlacements == layout.placements
}

/**
 * 目前桌況中仍應具有實體牌牆 placement 的所有牌張 Uuid。
 *
 * @return 活牌區與規則保留區中的牌張 ID 集合。
 */
private fun TableState.physicalWallTileIds(): Set<Uuid> = (tileWall.getAllTiles() + reservedWallTiles).mapTo(mutableSetOf()) { it.id }

/**
 * 驗證實體牌牆拒絕原因使用完整 namespaced ID。
 *
 * @param reasonId policy 提供的拒絕原因 ID。
 * @throws IllegalArgumentException 當 ID 不是合法的 namespaced ID 時拋出。
 */
private fun requirePhysicalWallReasonId(reasonId: String) {
    NamespacedId.requireValid(reasonId) {
        "Physical wall layout rejection must use a namespaced id: $reasonId"
    }
}
