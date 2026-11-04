package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawContext
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.drawFromReservedWall

/** 日本麻將槓後（與三人麻將拔北後）嶺上補牌與死牌區補充 policy。 */
object RiichiSupplementalDrawPolicy : SupplementalDrawPolicy {
    /**
     * 解析成功成立的槓與拔北；其他動作不需要補牌。
     *
     * 每次取走死牌區最前方的嶺上牌，再將活牌尾端補入死牌區末端。補入的牌只用來維持死牌區
     * 張數，不會成為後續可摸取的嶺上牌。槓與拔北各自最多補牌 [MAX_SUPPLEMENTAL_DRAWS] 次，
     * 分別記在 [RiichiWallProgress.completedSupplementalDrawCount] 與 [RiichiWallProgress.completedNorthDrawCount]。
     */
    override fun resolve(context: SupplementalDrawContext): SupplementalDrawDecision {
        val isPullNorth = context.action == PULL_NORTH_GAME_ACTION
        if (context.action !is GameAction.Kan && !isPullNorth) return SupplementalDrawDecision.NotRequired
        val wallProgress = context.tableStateBeforeAction.components[RiichiTableComponents.WALL_PROGRESS]
            ?: return SupplementalDrawDecision.Rejected(INVALID_STATE_REASON_ID)
        val drawIndex = if (isPullNorth) wallProgress.completedNorthDrawCount else wallProgress.completedSupplementalDrawCount
        if (drawIndex !in 0 until MAX_SUPPLEMENTAL_DRAWS) {
            return SupplementalDrawDecision.Rejected(LIMIT_REACHED_REASON_ID)
        }

        val draw = drawFromReservedWall(context.tableStateAfterAction)
            ?: return SupplementalDrawDecision.Rejected(SupplementalDrawReasonIds.WALL_EXHAUSTED)
        val updatedProgress = if (isPullNorth) {
            wallProgress.copy(completedNorthDrawCount = drawIndex + 1)
        } else {
            wallProgress.copy(completedSupplementalDrawCount = drawIndex + 1)
        }

        return draw.copy(
            tableComponents = updatedProgress.toTableComponentPatch(wallProgress),
        )
    }

    /** 槓（或拔北）各自在一局內最多補牌的次數。 */
    const val MAX_SUPPLEMENTAL_DRAWS = 4

    /** 本局補牌次數超過規則上限的原因 ID。 */
    const val LIMIT_REACHED_REASON_ID = "mahjongcraft:riichi/supplemental_draw_limit_reached"

    /** 桌況缺少本局牌山進度的原因 ID。 */
    const val INVALID_STATE_REASON_ID = "mahjongcraft:riichi/invalid_wall_progress"
}
