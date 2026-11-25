package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerDecisionPhase
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTimeSource
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContextResolver
import kotlin.uuid.Uuid

/**
 * 這場對局目前正在等 [playerId] 做哪一種決定。
 *
 * 規則選擇中、而且這位玩家還沒提交時，以規則選擇為準。否則依桌況判斷是否輪到他打牌或回應別人的牌。
 *
 * @param playerId 要查詢的玩家。
 * @param actionContexts 依桌況解析操作權的解析器。
 * @return 正在等待的決定種類。沒有在等這位玩家時為 null。
 */
internal fun Game.decisionPhaseFor(playerId: Uuid, actionContexts: PlayerActionContextResolver): PlayerDecisionPhase? {
    val ruleDecision = pendingRuleDecision
    if (ruleDecision != null && playerId in ruleDecision.participantPlayerIds && playerId !in ruleDecision.completedPlayerIds) {
        return when (ruleDecision.step.timeSource) {
            RuleDecisionTimeSource.RULE_DECISION_TIME -> PlayerDecisionPhase.RULE_DECISION
            RuleDecisionTimeSource.ACTION_TIME -> PlayerDecisionPhase.RULE_ACTION
        }
    }
    return actionContexts.resolveFor(tableState, playerId)?.phase
}
