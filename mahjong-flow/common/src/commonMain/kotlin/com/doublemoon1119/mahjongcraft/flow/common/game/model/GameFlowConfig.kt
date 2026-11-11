package com.doublemoon1119.mahjongcraft.flow.common.game.model

/**
 * 與麻將規則無關的遊戲流程設定。
 *
 * @property timeControl 一般回合、反應決策及比照一般動作計時的規則選擇（[PlayerDecisionPhase.OWN_TURN]、
 *   [PlayerDecisionPhase.DISCARD_REACTION]、[PlayerDecisionPhase.ROBBING_REACTION]、[PlayerDecisionPhase.RULE_ACTION]）
 *   採用的基本思考時間；[ActionTimeControl.reserve] 是
 *   玩家整場遊戲共用的保留思考時間資源池，開局時依這個值建立（見 [Game.remainingReserveByPlayerId]），
 *   上述階段共用同一份；使用獨立規則選擇時間的階段不使用這個保留時間池。
 * @property ruleDecision 獨立的規則選擇時間設定；規則沒有這類選擇時為 null。
 * @property decisionTimeoutPolicy 玩家耗盡全部思考時間後採用的流程政策。
 * @property spectatingPolicy 外部玩家是否可以旁觀進行中的遊戲。
 * @property spectatorHandVisibility 旁觀者可見的手牌範圍。
 */
data class GameFlowConfig(
    val timeControl: ActionTimeControl = ActionTimeControl.Normal,
    val ruleDecision: RuleDecisionConfig? = null,
    val decisionTimeoutPolicy: DecisionTimeoutPolicy = DecisionTimeoutPolicy.FORCED_AUTO_PLAY,
    val spectatingPolicy: SpectatingPolicy = SpectatingPolicy.ENABLED,
    val spectatorHandVisibility: SpectatorHandVisibility = SpectatorHandVisibility.REVEALED,
)
