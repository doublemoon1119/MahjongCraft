package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector

/**
 * 一張牌在畫面上的絕對位置與姿態，不包含模型內部的牌底補償。
 *
 * @property position 牌底的世界座標。
 * @property yawDegrees 世界朝向，使用與實體相同的角度慣例。
 * @property poseDegrees 牌的局部翻轉角；零代表直立。
 * @property lean 移動途中往前進方向的傾斜；靜止時為 [TileLean.NONE]。
 */
data class TileDisplayTransform(
    val position: DiceAnimationVector,
    val yawDegrees: Float,
    val poseDegrees: Float,
    val lean: TileLean = TileLean.NONE,
)
