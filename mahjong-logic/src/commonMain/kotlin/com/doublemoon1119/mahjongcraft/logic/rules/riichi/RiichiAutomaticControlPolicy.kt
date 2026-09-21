package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.AutomaticControlAction
import com.doublemoon1119.mahjongcraft.logic.module.AutomaticControlContext
import com.doublemoon1119.mahjongcraft.logic.module.AutomaticControlPolicy
import com.doublemoon1119.mahjongcraft.logic.module.AutomaticControlResult
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds

/** 日本麻將的自動和牌、不吃碰槓與安全摸切 policy。 */
object RiichiAutomaticControlPolicy : AutomaticControlPolicy {
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

    /** 判斷動作是否為日麻可自動接受的和牌。 */
    private fun isWinAction(action: GameAction): Boolean = action is GameAction.Ron || action == GameAction.Tsumo

    /** 判斷動作是否屬於「不吃碰槓」會拒絕的公開鳴牌。 */
    private fun isDeclinableCall(action: GameAction): Boolean = when (action) {
        is GameAction.Chi, is GameAction.Pon -> true
        is GameAction.Kan -> action.type == GameAction.KanType.OPEN_KAN
        else -> false
    }
}
