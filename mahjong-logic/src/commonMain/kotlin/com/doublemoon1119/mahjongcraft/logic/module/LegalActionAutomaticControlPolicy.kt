package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction

/**
 * 只依目前合法動作決定的自動和牌、自動跳過鳴牌與自動打出摸到的牌。
 *
 * 不含任何規則專屬判斷：能不能和、能不能鳴牌都已由規則的合法動作決定，有其他可做的動作（例如宣告）時不會自動打牌。
 */
object LegalActionAutomaticControlPolicy : AutomaticControlPolicy {
    /** 依固定優先序解析目前可以立即完成或隱藏的動作。 */
    override fun evaluate(context: AutomaticControlContext): AutomaticControlResult {
        val enabled = context.enabledControlIds
        val legalActions = context.legalActions

        if (BuiltInAutomaticControlIds.AUTO_WIN in enabled) {
            legalActions.firstOrNull(::isWinAction)?.let { action ->
                return AutomaticControlResult(immediateAction = AutomaticControlAction(action))
            }
        }

        val hiddenCalls = if (BuiltInAutomaticControlIds.DECLINE_CALLS in enabled) {
            legalActions.filterTo(mutableSetOf(), ::isDeclinableCall)
        } else {
            emptySet()
        }
        val visibleActions = legalActions.filterNot { it in hiddenCalls }
        if (hiddenCalls.isNotEmpty() && visibleActions == listOf(GameAction.Pass)) {
            return AutomaticControlResult(
                immediateAction = AutomaticControlAction(GameAction.Pass),
                hiddenActions = hiddenCalls,
            )
        }

        val lastDrawn = context.player.hand.lastDrawn
        if (
            BuiltInAutomaticControlIds.AUTO_TSUMOGIRI in enabled &&
            lastDrawn != null &&
            visibleActions.isEmpty()
        ) {
            return AutomaticControlResult(
                immediateAction = AutomaticControlAction(GameAction.Discard(lastDrawn.id)),
                hiddenActions = hiddenCalls,
            )
        }

        return AutomaticControlResult(hiddenActions = hiddenCalls)
    }

    /**
     * 判斷動作是否為可自動接受的和牌。
     *
     * @param action 待檢查的遊戲動作。
     * @return 動作為和別人打出的牌或自摸時為 true。
     */
    private fun isWinAction(action: GameAction): Boolean = action is GameAction.Ron || action == GameAction.Tsumo

    /**
     * 判斷動作是否屬於「不吃碰槓」會拒絕的公開鳴牌。
     *
     * @param action 待檢查的遊戲動作。
     * @return 動作為吃、碰或明槓時為 true。
     */
    private fun isDeclinableCall(action: GameAction): Boolean = when (action) {
        is GameAction.Chi, is GameAction.Pon -> true
        is GameAction.Kan -> action.type == GameAction.KanType.OPEN_KAN
        else -> false
    }
}
