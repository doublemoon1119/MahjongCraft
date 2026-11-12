package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealCheckpoint
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealContext
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealDecision
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealPolicy

/** 日本麻將槓寶牌立即公開、延後公開與取消的純邏輯 policy；三人日麻拔北後的補牌不會公開槓寶牌。 */
object RiichiWallRevealPolicy : WallRevealPolicy {
    /** 根據規則中立流程節點更新日麻槓寶牌公開狀態。 */
    override fun resolve(context: WallRevealContext): WallRevealDecision {
        // 沒有本局進度就無法繼續日麻指示牌流程。
        val state = context.tableState.components[RiichiTableComponents.WALL_PROGRESS]
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

    /**
     * 依來源動作決定新指示牌立即公開或加入等待佇列；拔北的補牌不公開新指示牌。
     *
     * @param context 補牌完成的節點情境。
     * @param state 目前牌山進度。
     * @return 新狀態、無變更或拒絕原因。
     */
    private fun afterSupplementalDraw(
        context: WallRevealContext,
        state: RiichiWallProgress,
    ): WallRevealDecision {
        if (context.sourceAction == RIICHI_PULL_NORTH_GAME_ACTION) return WallRevealDecision.NoChange
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
                WallRevealDecision.Updated(state.copy(pendingKanDoraReveals = state.pendingKanDoraReveals + pending).toTableComponentPatch(state))
            }
        }
    }

    /**
     * 公開目前所有等待項目。
     *
     * @param context 目前公開節點與桌況。
     * @param state 含待公開項目的牌山進度。
     * @return 更新結果；沒有等待項目時為無變更。
     */
    private fun revealPending(context: WallRevealContext, state: RiichiWallProgress): WallRevealDecision {
        val targetCount = state.pendingKanDoraReveals.maxOfOrNull { it.supplementalDrawNumber }
            ?: return WallRevealDecision.NoChange
        return revealThrough(context, state, targetCount)
    }

    /**
     * 將公開進度推進到 [targetCount]，並回傳新公開的實際牌張。
     *
     * @param context 提供指示牌位置的桌況情境。
     * @param state 原始公開進度與待處理項目。
     * @param targetCount 要公開至第幾次槓補牌。
     * @return 含新增公開牌識別碼的更新；進度或牌數不符時拒絕。
     */
    private fun revealThrough(
        context: WallRevealContext,
        state: RiichiWallProgress,
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
        val before = state.getDoraIndicators(context.tableState).first.mapTo(mutableSetOf()) { it.id }
        val after = updated.getDoraIndicators(context.tableState).first.mapTo(mutableSetOf()) { it.id }
        val newlyRevealedTileIds = after - before
        val expectedNewTileCount = targetCount - state.revealedKanDoraCount
        return if (newlyRevealedTileIds.size == expectedNewTileCount) {
            WallRevealDecision.Updated(updated.toTableComponentPatch(state), newlyRevealedTileIds)
        } else {
            WallRevealDecision.Rejected(INVALID_STATE_REASON_ID)
        }
    }

    /**
     * 胡牌成立時取消尚未公開的項目。
     *
     * @param state 目前牌山進度。
     * @return 清除等待項目的更新；原本為空時不變。
     */
    private fun cancelPending(state: RiichiWallProgress): WallRevealDecision = if (state.pendingKanDoraReveals.isEmpty()) {
        WallRevealDecision.NoChange
    } else {
        WallRevealDecision.Updated(state.copy(pendingKanDoraReveals = emptyList()).toTableComponentPatch(state))
    }

    /**
     * 驗證公開進度與等待項目沒有超出已完成補牌範圍。
     *
     * @param context 提供目前玩家識別碼的桌況情境。
     * @return 次數在範圍內、等待槓次不重複且來源玩家存在時為 true。
     */
    private fun RiichiWallProgress.isValid(context: WallRevealContext): Boolean {
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

    /** 無法解析日麻牌山進度的原因 ID。 */
    const val INVALID_STATE_REASON_ID = "mahjongcraft:riichi/invalid_wall_reveal_state"

    /** 節點缺少必要來源動作或玩家的原因 ID。 */
    const val INVALID_ACTION_REASON_ID = "mahjongcraft:riichi/invalid_wall_reveal_action"
}
