package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import kotlinx.serialization.Serializable

/** 牌面的受控方向，供平台呈現鳴牌 popup 使用。 */
@Serializable
enum class DecisionTileOrientationDto {
    /** 牌面保持正向。 */
    UPRIGHT,

    /** 牌面向左旋轉。 */
    ROTATED_LEFT,

    /** 牌面向右旋轉。 */
    ROTATED_RIGHT,
}
