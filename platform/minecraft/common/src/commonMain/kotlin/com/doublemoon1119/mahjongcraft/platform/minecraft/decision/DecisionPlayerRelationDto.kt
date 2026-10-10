package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import kotlinx.serialization.Serializable

/** 觸發他家反應的玩家相對位置。 */
@Serializable
enum class DecisionPlayerRelationDto {
    LEFT,
    ACROSS,
    RIGHT,
}
