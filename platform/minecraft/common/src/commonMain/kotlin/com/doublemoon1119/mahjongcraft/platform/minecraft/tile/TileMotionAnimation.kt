package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceRollAnimation
import kotlin.math.PI
import kotlin.math.sin

/**
 * 由 server tick 與起點偏移產生可重建牌張運動路徑的純計算器；跟 [DiceRollAnimation] 不同，牌張運動
 * 不需要隨機物理，同一組輸入永遠算出同一個 frame，不需要吃 seed。
 *
 * @property spec 此動畫使用的時間與幅度參數。
 */
class TileMotionAnimation(
    private val spec: TileMotionAnimationSpec,
) {
    /**
     * 計算指定經過 ticks、指定起點相對終點偏移的視覺 frame。
     *
     * @param elapsedTicks 已經過的 server ticks。
     * @param startOffset 起點相對於終點的位移。
     * @param startYawOffsetDegrees 起點相對終點的 yaw 差。
     * @return 指定時間點的動畫狀態。
     */
    fun frame(
        elapsedTicks: Double,
        startOffset: DiceAnimationVector,
        startYawOffsetDegrees: Float = 0.0f,
    ): TileMotionAnimationFrame {
        val progress = (elapsedTicks / spec.durationTicks).coerceIn(0.0, 1.0)
        if (progress >= 1.0) {
            return TileMotionAnimationFrame(
                offset = DiceAnimationVector.ZERO,
                poseRotationDegrees = spec.endPoseRotationDegrees,
                yawOffsetDegrees = 0.0f,
                progress = 1.0,
                completed = true,
            )
        }

        // 位移使用 ease-out；姿態旋轉預設線性內插，只有 [TileMotionAnimationSpec.easeRotation] 為 true
        // 時才使用相同的 ease-out 進度。
        val remaining = 1.0 - progress
        val easedProgress = 1.0 - remaining * remaining
        val offset = DiceAnimationVector(
            x = startOffset.x * (1.0 - easedProgress),
            y = startOffset.y * (1.0 - easedProgress) + sin(PI * progress) * spec.arcHeight,
            z = startOffset.z * (1.0 - easedProgress),
        )
        val rotationProgress = if (spec.easeRotation) easedProgress else progress
        val poseRotationDegrees = spec.startPoseRotationDegrees +
            (spec.endPoseRotationDegrees - spec.startPoseRotationDegrees) * rotationProgress.toFloat()
        return TileMotionAnimationFrame(
            offset = offset,
            poseRotationDegrees = poseRotationDegrees,
            yawOffsetDegrees = startYawOffsetDegrees * (1.0 - easedProgress).toFloat(),
            progress = progress,
            completed = false,
        )
    }
}
