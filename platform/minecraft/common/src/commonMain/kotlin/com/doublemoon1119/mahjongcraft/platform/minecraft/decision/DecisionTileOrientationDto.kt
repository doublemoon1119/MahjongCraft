package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import kotlinx.serialization.Serializable

/** 牌面的受控方向，供平台呈現鳴牌 popup 使用。 */
@Serializable
enum class DecisionTileOrientationDto {
    UPRIGHT,
    ROTATED_LEFT,
    ROTATED_RIGHT,
}
