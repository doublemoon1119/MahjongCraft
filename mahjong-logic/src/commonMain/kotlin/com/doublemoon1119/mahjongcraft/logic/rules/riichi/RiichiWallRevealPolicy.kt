package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealCheckpoint
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealContext
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealDecision
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealPolicy

/** 日本麻將槓寶牌立即公開、延後公開與取消的純邏輯 policy。 */
object RiichiWallRevealPolicy : WallRevealPolicy {
    /** 根據規則中立流程節點更新日麻槓寶牌公開狀態。 */
    override fun resolve(context: WallRevealContext): WallRevealDecision {
        val state = context.tableState.dynamicRuleState as? RiichiDynamicState
            ?: return WallRevealDecision.Rejected(INVALID_STATE_REASON_ID)
        if (!state.isValid(context)) return WallRevealDecision.Rejected(INVALID_STATE_REASON_ID)

        return when (context.checkpoint) {
            WallRevealCheckpoint.AFTER_SUPPLEMENTAL_DRAW -> afterSupplementalDraw(context, state)
            WallRevealCheckpoint.BEFORE_SUPPLEMENTAL_DRAW,
            WallRevealCheckpoint.AFTER_DISCARD_REACTIONS,
            -> revealPending(context, state)

            WallRevealCheckpoint.WIN_CONFIRMED -> cancelPending(state)
        }
    }

    /** 依來源動作決定新指示牌立即公開或加入等待佇列。 */
    private fun afterSupplementalDraw(
        context: WallRevealContext,
        state: RiichiDynamicState,
    ): WallRevealDecision {
        val action = context.sourceAction as? GameAction.Kan
            ?: return WallRevealDecision.Rejected(INVALID_ACTION_REASON_ID)
        val actorPlayerId = context.actorPlayerId
            ?: return WallRevealDecision.Rejected(INVALID_ACTION_REASON_ID)
        val drawNumber = state.completedSupplementalDrawCount
        if (drawNumber <= 0) return WallRevealDecision.Rejected(INVALID_STATE_REASON_ID)

        return if (action.type == GameAction.KanType.CLOSED_KAN) {
            revealThrough(context, state, drawNumber)
        } else {
            val pending = RiichiPendingKanDoraReveal(actorPlayerId, action.type, drawNumber)
            if (state.pendingKanDoraReveals.any { it.supplementalDrawNumber == drawNumber }) {
                WallRevealDecision.Rejected(INVALID_STATE_REASON_ID)
            } else {
                WallRevealDecision.Updated(state.copy(pendingKanDoraReveals = state.pendingKanDoraReveals + pending))
            }
        }
    }

    /** 公開目前所有等待項目。 */
    private fun revealPending(context: WallRevealContext, state: RiichiDynamicState): WallRevealDecision {
        val targetCount = state.pendingKanDoraReveals.maxOfOrNull { it.supplementalDrawNumber }
            ?: return WallRevealDecision.NoChange
        return revealThrough(context, state, targetCount)
    }

    /** 將公開進度推進到 [targetCount]，並回傳新公開的實際牌張。 */
    private fun revealThrough(
        context: WallRevealContext,
        state: RiichiDynamicState,
        targetCount: Int,
    ): WallRevealDecision {
        if (targetCount !in state.revealedKanDoraCount..state.completedSupplementalDrawCount) {
            return WallRevealDecision.Rejected(INVALID_STATE_REASON_ID)
        }
        val updated = state.copy(
            revealedKanDoraCount = targetCount,
            pendingKanDoraReveals = state.pendingKanDoraReveals.filter {
                it.supplementalDrawNumber > targetCount
            },
        )
        val before = state.getVisibleTileIds(context.tableState)
        val after = updated.getVisibleTileIds(context.tableState.copy(dynamicRuleState = updated))
        val newlyRevealedTileIds = after - before
        val expectedNewTileCount = targetCount - state.revealedKanDoraCount
        return if (newlyRevealedTileIds.size == expectedNewTileCount) {
            WallRevealDecision.Updated(updated, newlyRevealedTileIds)
        } else {
            WallRevealDecision.Rejected(INVALID_STATE_REASON_ID)
        }
    }

    /** 胡牌成立時取消尚未公開的項目。 */
    private fun cancelPending(state: RiichiDynamicState): WallRevealDecision = if (state.pendingKanDoraReveals.isEmpty()) {
        WallRevealDecision.NoChange
    } else {
        WallRevealDecision.Updated(state.copy(pendingKanDoraReveals = emptyList()))
    }

    /** 驗證公開進度與等待項目沒有超出已完成補牌範圍。 */
    private fun RiichiDynamicState.isValid(context: WallRevealContext): Boolean {
        val pendingNumbers = pendingKanDoraReveals.map { it.supplementalDrawNumber }
        val playerIds = context.tableState.players.mapTo(mutableSetOf()) { it.id }
        return completedSupplementalDrawCount in 0..RiichiSupplementalDrawPolicy.MAX_SUPPLEMENTAL_DRAWS &&
            revealedKanDoraCount in 0..completedSupplementalDrawCount &&
            pendingNumbers.distinct().size == pendingNumbers.size &&
            pendingKanDoraReveals.all {
                it.sourceKanType != GameAction.KanType.CLOSED_KAN &&
                    it.actorPlayerId in playerIds &&
                    it.supplementalDrawNumber in (revealedKanDoraCount + 1)..completedSupplementalDrawCount
            }
    }

    /** 無法解析日麻動態狀態的原因 ID。 */
    const val INVALID_STATE_REASON_ID = "mahjongcraft:invalid_wall_reveal_state"

    /** 節點缺少必要來源動作或玩家的原因 ID。 */
    const val INVALID_ACTION_REASON_ID = "mahjongcraft:invalid_wall_reveal_action"
}
