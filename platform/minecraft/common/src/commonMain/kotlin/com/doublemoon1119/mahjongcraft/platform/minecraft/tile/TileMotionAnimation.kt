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
    /** 計算指定經過 ticks、指定起點相對終點偏移的視覺 frame。 */
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

        // 位移用 ease-out（1 減去「1 減 progress」的平方）取代原始 progress 直接線性內插——遊戲內
        // 實際比較過先慢後快（ease-in）跟先快後慢（ease-out）兩種曲線，後者落地前明顯減速的感覺更好；
        // 姿態旋轉角預設維持線性內插，不受此影響（旋轉的速度感不需要跟位移的曲線綁在一起）——只有
        // [TileMotionAnimationSpec.easeRotation] 明確要求時才套用同一條 ease-out 曲線，見該欄位 KDoc。
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
