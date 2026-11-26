package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

/**
 * 正式擲骰時單顆骰子的版本無關呈現位置。
 *
 * @property finalPosition 動畫結束後骰子底部中心的世界座標。
 * @property startOffset 動畫起點相對 [finalPosition] 的向量。
 * @property startDelayTicks 相對同次擲骰開始時間的延遲 tick 數。
 */
data class MahjongDiceTablePlacement(
    val finalPosition: DiceAnimationVector,
    val startOffset: DiceAnimationVector,
    val startDelayTicks: Int,
)
