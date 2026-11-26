package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import kotlinx.serialization.Serializable

/** 結算的種類。 */
@Serializable
enum class RoundResultKindDto {
    /** 和牌，或規則視同和牌的特殊結果。 */
    WIN,

    /** 流局，包含途中流局。 */
    DRAW,
}
