package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector

/**
 * renderer 在指定時間需要套用的視覺位移與姿態旋轉角。
 *
 * @property offset 相對 entity 最終位置的視覺位移。
 * @property poseRotationDegrees 內插後的姿態旋轉角，取代靜態的 `tilePose.rotationDegrees`。
 * @property yawOffsetDegrees 畫面目前相對 entity 真實終點 yaw 的角差。
 * @property progress 限制在 `0～1` 的動畫進度。
 * @property completed 動畫是否已完成。
 */
data class TileMotionAnimationFrame(
    val offset: DiceAnimationVector,
    val poseRotationDegrees: Float,
    val yawOffsetDegrees: Float,
    val progress: Double,
    val completed: Boolean,
)
