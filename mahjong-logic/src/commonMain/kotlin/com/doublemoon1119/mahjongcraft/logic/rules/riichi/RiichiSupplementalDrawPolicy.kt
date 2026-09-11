package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawContext
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds

/** 日本麻將槓後嶺上補牌、死牌區補充與槓寶牌公開 policy。 */
object RiichiSupplementalDrawPolicy : SupplementalDrawPolicy {
    /**
     * 解析成功成立的槓；其他動作不需要補牌。
     *
     * 每次取走死牌區最前方的嶺上牌，再將活牌尾端補入死牌區末端。補入的牌只用來維持死牌區
     * 十四張，不會成為後續可摸取的嶺上牌。
     */
    override fun resolve(context: SupplementalDrawContext): SupplementalDrawDecision {
        if (context.action !is GameAction.Kan) return SupplementalDrawDecision.NotRequired
        val dynamicState = context.tableStateBeforeAction.dynamicRuleState as? RiichiDynamicState
            ?: return SupplementalDrawDecision.Rejected(INVALID_STATE_REASON_ID)
        val drawIndex = dynamicState.completedSupplementalDrawCount
        if (drawIndex !in 0 until MAX_SUPPLEMENTAL_DRAWS) {
            return SupplementalDrawDecision.Rejected(LIMIT_REACHED_REASON_ID)
        }

        val drawnTile = context.tableStateAfterAction.reservedWallTiles.firstOrNull()
            ?: return SupplementalDrawDecision.Rejected(SupplementalDrawReasonIds.WALL_EXHAUSTED)
        val replenishment = context.tableStateAfterAction.tileWall.drawLast()
        val replenishmentTile = replenishment.tile
            ?: return SupplementalDrawDecision.Rejected(SupplementalDrawReasonIds.WALL_EXHAUSTED)
        val updatedDeadWall = context.tableStateAfterAction.reservedWallTiles.drop(1) + replenishmentTile
        val updatedDynamicState = dynamicState.copy(completedSupplementalDrawCount = drawIndex + 1)
        val updatedState = context.tableStateAfterAction.copy(
            tileWall = replenishment.wall,
            initialDeadWall = updatedDeadWall,
            dynamicRuleState = updatedDynamicState,
        )
        val previouslyVisible = dynamicState.getVisibleTileIds(context.tableStateBeforeAction)
        val newlyVisible = updatedDynamicState.getVisibleTileIds(updatedState) - previouslyVisible

        return SupplementalDrawDecision.Completed(
            drawnTiles = listOf(drawnTile),
            tileWall = replenishment.wall,
            reservedWallTiles = updatedDeadWall,
            dynamicRuleState = updatedDynamicState,
            newlyRevealedTileIds = newlyVisible,
        )
    }

    /** 日麻死牌區前段可供嶺上補牌的語意槽位數。 */
    const val MAX_SUPPLEMENTAL_DRAWS = 4

    /** 本局補牌次數超過規則上限的原因 ID。 */
    const val LIMIT_REACHED_REASON_ID = "mahjongcraft:supplemental_draw_limit_reached"

    /** 桌況缺少日麻動態狀態的原因 ID。 */
    const val INVALID_STATE_REASON_ID = "mahjongcraft:invalid_riichi_dynamic_state"
}
