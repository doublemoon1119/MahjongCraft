package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceRollAnimation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

        // 對稱拋物線的位移使用 ease-out；「提起、橫移、放下」的橫移在中段以先慢後快再慢的曲線完成，高度在前後兩段
        // 提起與放下。姿態旋轉預設線性內插，只有 [TileMotionAnimationSpec.easeRotation] 為 true 時才使用相同的進度。
        val remaining = 1.0 - progress
        val easedProgress = if (spec.liftCarryLower) {
            smoothStep((progress - CARRY_START) / (CARRY_END - CARRY_START))
        } else {
            1.0 - remaining * remaining
        }
        val lift = if (spec.liftCarryLower) {
            minOf(smoothStep(progress / LIFT_END), smoothStep((1.0 - progress) / (1.0 - LOWER_START)))
        } else {
            sin(PI * progress)
        }
        val offset = DiceAnimationVector(
            x = startOffset.x * (1.0 - easedProgress),
            y = startOffset.y * (1.0 - easedProgress) + lift * spec.arcHeight,
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
            lean = lean(progress, startOffset),
        )
    }

    /**
     * 往前進方向的傾斜：提起時從零倒到 [TileMotionAnimationSpec.leanDegrees]，橫移時維持，放下時先轉成前角朝下再回正。
     * 旋轉軸是世界的水平軸，與往上方向及前進方向垂直，所以不受座位朝向影響。
     *
     * @param progress 動畫進度。
     * @param startOffset 起點相對於終點的位移，前進方向與它相反。
     * @return 這一刻的傾斜；沒有設定傾斜或沒有水平移動時為 [TileLean.NONE]。
     */
    private fun lean(progress: Double, startOffset: DiceAnimationVector): TileLean {
        if (!spec.liftCarryLower || spec.leanDegrees == 0.0f) return TileLean.NONE
        val travelX = -startOffset.x
        val travelZ = -startOffset.z
        val length = sqrt(travelX * travelX + travelZ * travelZ)
        if (length < 1e-9) return TileLean.NONE
        val amount = when {
            progress < LIFT_END -> smoothStep(progress / LIFT_END)
            progress < LOWER_START -> 1.0
            else -> {
                val lowering = (progress - LOWER_START) / (1.0 - LOWER_START)
                cos(PI * lowering) * (1.0 - lowering)
            }
        }
        val degrees = (spec.leanDegrees * amount).toFloat()
        if (degrees == 0.0f) return TileLean.NONE
        // 繞「往上 × 前進方向」旋轉時，上端倒向前進方向；加 0.0 讓負零成為零。
        return TileLean(axisX = travelZ / length + 0.0, axisZ = -travelX / length + 0.0, degrees = degrees)
    }

    /**
     * 0 到 1 之間先慢後快再慢的曲線；超出範圍時固定在兩端。
     *
     * @param value 輸入進度。
     * @return 平滑後的進度。
     */
    private fun smoothStep(value: Double): Double {
        val clamped = value.coerceIn(0.0, 1.0)
        return clamped * clamped * (3.0 - 2.0 * clamped)
    }

    /** 「提起、橫移、放下」各段的進度分界。 */
    private companion object {
        /** 提起到最高點的進度。 */
        const val LIFT_END: Double = 0.18

        /** 開始橫移的進度；在提到最高點之前開始，讓轉角圓滑。 */
        const val CARRY_START: Double = 0.12

        /** 橫移完成的進度；在開始放下之後才完成，讓轉角圓滑。 */
        const val CARRY_END: Double = 0.82

        /** 開始放下的進度。 */
        const val LOWER_START: Double = 0.75
    }
}
