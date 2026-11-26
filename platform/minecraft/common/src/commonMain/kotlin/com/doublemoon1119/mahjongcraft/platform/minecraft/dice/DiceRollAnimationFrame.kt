package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

/**
 * renderer 在指定時間需要套用的視覺位移與額外旋轉。
 *
 * @property offset 相對 entity 最終位置的視覺位移。
 * @property rotation 在最終點數姿態之前套用的額外旋轉。
 * @property progress 限制在 `0～1` 的動畫進度。
 * @property completed 動畫是否已完成。
 */
data class DiceRollAnimationFrame(
    val offset: DiceAnimationVector,
    val rotation: DiceAnimationQuaternion,
    val progress: Double,
    val completed: Boolean,
)
