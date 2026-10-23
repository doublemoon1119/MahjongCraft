package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import kotlinx.serialization.Serializable

/** 玩家目前取得決策權的網路階段。 */
@Serializable
enum class PlayerDecisionPhaseDto {
    /** 玩家正在進行規則選擇。 */
    RULE_DECISION,

    /** 玩家自己的回合。 */
    OWN_TURN,

    /** 玩家正在回應其他玩家的捨牌。 */
    DISCARD_REACTION,

    /** 玩家正在回應搶和視窗。 */
    ROBBING_REACTION,
}
