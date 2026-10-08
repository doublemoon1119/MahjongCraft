package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawContext
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds

/** 日本麻將槓後（與三人麻將拔北後）嶺上補牌與死牌區補充 policy。 */
object RiichiSupplementalDrawPolicy : SupplementalDrawPolicy {
    /**
     * 解析成功成立的槓與拔北；其他動作不需要補牌。
     *
     * 每次取走死牌區最前方的嶺上牌，再將活牌尾端補入死牌區末端。補入的牌只用來維持死牌區
     * 張數，不會成為後續可摸取的嶺上牌。槓與拔北各自最多補牌 [MAX_SUPPLEMENTAL_DRAWS] 次，
     * 分別記在 [RiichiDynamicState.completedSupplementalDrawCount] 與 [RiichiDynamicState.completedNorthDrawCount]。
     */
    override fun resolve(context: SupplementalDrawContext): SupplementalDrawDecision {
        val isPullNorth = context.action == PULL_NORTH_GAME_ACTION
        if (context.action !is GameAction.Kan && !isPullNorth) return SupplementalDrawDecision.NotRequired
        val dynamicState = context.tableStateBeforeAction.dynamicRuleState as? RiichiDynamicState
            ?: return SupplementalDrawDecision.Rejected(INVALID_STATE_REASON_ID)
        val drawIndex = if (isPullNorth) dynamicState.completedNorthDrawCount else dynamicState.completedSupplementalDrawCount
        if (drawIndex !in 0 until MAX_SUPPLEMENTAL_DRAWS) {
            return SupplementalDrawDecision.Rejected(LIMIT_REACHED_REASON_ID)
        }

        val drawnTile = context.tableStateAfterAction.reservedWallTiles.firstOrNull()
            ?: return SupplementalDrawDecision.Rejected(SupplementalDrawReasonIds.WALL_EXHAUSTED)
        val replenishment = context.tableStateAfterAction.tileWall.drawLast()
        val replenishmentTile = replenishment.tile
            ?: return SupplementalDrawDecision.Rejected(SupplementalDrawReasonIds.WALL_EXHAUSTED)
        val updatedDeadWall = context.tableStateAfterAction.reservedWallTiles.drop(1) + replenishmentTile
        val updatedDynamicState = if (isPullNorth) {
            dynamicState.copy(completedNorthDrawCount = drawIndex + 1)
        } else {
            dynamicState.copy(completedSupplementalDrawCount = drawIndex + 1)
        }

        return SupplementalDrawDecision.Completed(
            drawnTiles = listOf(drawnTile),
            tileWall = replenishment.wall,
            reservedWallTiles = updatedDeadWall,
            dynamicRuleState = updatedDynamicState,
        )
    }

    /** 槓（或拔北）各自在一局內最多補牌的次數。 */
    const val MAX_SUPPLEMENTAL_DRAWS = 4

    /** 本局補牌次數超過規則上限的原因 ID。 */
    const val LIMIT_REACHED_REASON_ID = "mahjongcraft:riichi/supplemental_draw_limit_reached"

    /** 桌況缺少日麻動態狀態的原因 ID。 */
    const val INVALID_STATE_REASON_ID = "mahjongcraft:riichi/invalid_dynamic_state"
}
