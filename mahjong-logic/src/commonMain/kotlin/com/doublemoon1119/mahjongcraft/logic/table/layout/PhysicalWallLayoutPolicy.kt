package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 規則提供的抽象實體牌牆布局能力。
 *
 * 規則只描述正規化位置與移動階段；平台負責將結果投影成世界座標、規劃碰撞安全路徑及播放動畫。
 */
interface PhysicalWallLayoutPolicy {
    /** 建立一局牌牆的初始實體布局。 */
    fun createInitialLayout(context: InitialPhysicalWallLayoutContext): InitialPhysicalWallLayoutDecision

    /** 根據已完成的遊戲動作與牌牆變化解析新的實體布局。 */
    fun resolveTransition(context: PhysicalWallLayoutTransitionContext): PhysicalWallLayoutTransitionDecision
}

/**
 * 建立初始實體牌牆布局所需的規則中立資料。
 *
 * @property wallLayout 牌牆開門及分區完成後的權威布局結果。
 */
data class InitialPhysicalWallLayoutContext(val wallLayout: TileWallLayoutResult)

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

/**
 * 動作完成後解析實體牌牆演進所需的資料。
 *
 * @property tableStateBeforeAction 動作套用前的權威桌況。
 * @property tableStateAfterAction 動作及其規則牌牆變化套用後的候選桌況。
 * @property currentLayout 動作前的權威實體牌牆布局。
 * @property actorPlayerId 執行動作的玩家 Uuid。
 * @property action 實際完成的遊戲動作。
 */
data class PhysicalWallLayoutTransitionContext(
    val tableStateBeforeAction: TableState,
    val tableStateAfterAction: TableState,
    val currentLayout: TileWallPhysicalLayout,
    val actorPlayerId: Uuid,
    val action: GameAction,
)

/** 一個實體牌牆 transition 階段中，單張牌的宣告式移動。 */
data class PhysicalWallTileMove(
    /** 要移動的既有牌張 Uuid。 */
    val tileId: Uuid,
    /** 此階段開始時的權威抽象來源位置。 */
    val source: TileWallPlacement,
    /** 此移動完成後的抽象目的位置。 */
    val destination: TileWallPlacement,
)

/**
 * 同一段整體動作中的牌張移動集合。
 *
 * 同一階段允許平台為避免實體碰撞而細微錯開個別牌張的起步時間，但不得改變階段順序或最終位置。
 */
data class PhysicalWallLayoutTransitionPhase(val moves: List<PhysicalWallTileMove>) {
    init {
        require(moves.isNotEmpty()) { "Physical wall transition phase must contain at least one move" }
        require(moves.map { it.tileId }.distinct().size == moves.size) {
            "Physical wall transition phase must not move the same tile more than once"
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
     * @property layout 動畫完成後的唯一權威最終布局。
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

/** 實體牌牆布局共用的拒絕原因 ID。 */
object PhysicalWallLayoutReasonIds {
    /** Policy 結果未通過牌張集合、位置或 transition 引用驗證。 */
    const val INVALID_RESULT: String = "mahjongcraft:invalid_physical_wall_layout_result"
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

/** 建立並驗證初始實體牌牆布局。 */
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

/** 解析並驗證動作完成後的實體牌牆布局演進。 */
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

/** 此完成結果是否完整描述 [expectedTileIds] 的最終位置及所有實際位置變化。 */
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

/** 目前桌況中仍應具有實體牌牆 placement 的所有牌張 Uuid。 */
private fun TableState.physicalWallTileIds(): Set<Uuid> = (tileWall.getAllTiles() + reservedWallTiles).mapTo(mutableSetOf()) { it.id }

/** 驗證實體牌牆拒絕原因使用完整 namespaced ID。 */
private fun requirePhysicalWallReasonId(reasonId: String) {
    require(PHYSICAL_WALL_REASON_ID_PATTERN.matches(reasonId)) {
        "Physical wall layout rejection must use a namespaced id: $reasonId"
    }
}

/** 完整 namespaced ID 的格式。 */
private val PHYSICAL_WALL_REASON_ID_PATTERN: Regex = Regex("^[a-z0-9_.-]+:[a-z0-9/._-]+$")
