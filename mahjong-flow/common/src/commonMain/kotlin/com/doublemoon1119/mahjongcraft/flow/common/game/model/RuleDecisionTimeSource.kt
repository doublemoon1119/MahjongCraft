package com.doublemoon1119.mahjongcraft.flow.common.game.model

/** 規則選擇步驟取得思考時間的來源。 */
enum class RuleDecisionTimeSource {
    /** 使用規則選擇專用的固定時間，不消耗玩家的共用保留時間。 */
    RULE_DECISION_TIME,

    /** 比照一般遊戲動作，使用基本思考時間並消耗同一份共用保留時間。 */
    ACTION_TIME,
}
