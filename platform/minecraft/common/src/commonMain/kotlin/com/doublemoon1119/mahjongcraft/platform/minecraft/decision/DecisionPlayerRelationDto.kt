package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import kotlinx.serialization.Serializable

/** 觸發他家反應的玩家相對位置。 */
@Serializable
enum class DecisionPlayerRelationDto {
    /** 觸發玩家位於目前玩家的上家。 */
    LEFT,

    /** 觸發玩家位於目前玩家的對家。 */
    ACROSS,

    /** 觸發玩家位於目前玩家的下家。 */
    RIGHT,
}
