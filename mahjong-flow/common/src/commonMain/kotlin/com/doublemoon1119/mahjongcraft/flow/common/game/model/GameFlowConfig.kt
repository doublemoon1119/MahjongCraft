package com.doublemoon1119.mahjongcraft.flow.common.game.model

/**
 * 與麻將規則無關的遊戲流程設定。
 *
 * @property timeControl 一般回合／反應決策（[PlayerDecisionPhase.OWN_TURN]、[PlayerDecisionPhase.DISCARD_REACTION]、
 *   [PlayerDecisionPhase.ROBBING_REACTION]）採用的基本思考時間；[ActionTimeControl.reserveSeconds] 是
 *   玩家整場遊戲共用的保留思考時間資源池，開局時依這個值建立（見 [Game.remainingReserveMillisByPlayerId]），
 *   之後不分決策階段共用同一份，跟 [preparationBaseSeconds] 無關。
 * @property preparationBaseSeconds [PlayerDecisionPhase.ROUND_PREPARATION]（局前規則準備選擇，例如三人麻將
 *   開局選牌）專用的基本思考秒數；獨立於 [timeControl] 的基本思考秒數，因為選牌通常比單純回應一張牌需要
 *   更多思考時間。**沒有專屬的保留思考時間**——保留思考時間是整場共用的單一資源池（見 [timeControl]
 *   KDoc），耗盡 preparation 的基本思考時間一樣會動用同一份保留思考時間。
 * @property decisionTimeoutPolicy 玩家耗盡全部思考時間後採用的流程政策。
 * @property spectatingPolicy 外部玩家是否可以旁觀進行中的遊戲。
 * @property spectatorHandVisibility 旁觀者可見的手牌範圍。
 */
data class GameFlowConfig(
    val timeControl: ActionTimeControl = ActionTimeControl.Normal,
    val preparationBaseSeconds: Int = 30,
    val decisionTimeoutPolicy: DecisionTimeoutPolicy = DecisionTimeoutPolicy.FORCED_AUTO_PLAY,
    val spectatingPolicy: SpectatingPolicy = SpectatingPolicy.ENABLED,
    val spectatorHandVisibility: SpectatorHandVisibility = SpectatorHandVisibility.REVEALED,
) {
    init {
        require(preparationBaseSeconds > 0) { "Preparation base time must be positive" }
    }
}
