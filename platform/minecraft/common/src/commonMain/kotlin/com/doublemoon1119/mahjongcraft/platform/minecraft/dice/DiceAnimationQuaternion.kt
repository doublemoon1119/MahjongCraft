package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 用於確定性骰子旋轉的四元數，不依賴渲染函式庫；建構時不驗證單位長度。
 *
 * @property x X 虛部分量。
 * @property y Y 虛部分量。
 * @property z Z 虛部分量。
 * @property w 實部分量。
 */
data class DiceAnimationQuaternion(
    val x: Double,
    val y: Double,
    val z: Double,
    val w: Double,
) {
    /** 回傳正規化 quaternion；零長度值固定回傳 [IDENTITY]。 */
    fun normalized(): DiceAnimationQuaternion {
        val length = sqrt(x * x + y * y + z * z + w * w)
        return if (length == 0.0) IDENTITY else DiceAnimationQuaternion(x / length, y / length, z / length, w / length)
    }

    /**
     * 使用最短路徑球面插值至 [target]。
     *
     * @param target 插值終點 quaternion。
     * @param progress 插值進度；計算前會限制在 0 到 1。
     * @return 插值後的正規化 quaternion。
     */
    fun slerp(target: DiceAnimationQuaternion, progress: Double): DiceAnimationQuaternion {
        val start = normalized()
        var end = target.normalized()
        var dot = start.x * end.x + start.y * end.y + start.z * end.z + start.w * end.w
        if (dot < 0.0) {
            end = DiceAnimationQuaternion(-end.x, -end.y, -end.z, -end.w)
            dot = -dot
        }
        val clampedProgress = progress.coerceIn(0.0, 1.0)
        if (dot > LINEAR_INTERPOLATION_THRESHOLD) {
            return DiceAnimationQuaternion(
                x = start.x + (end.x - start.x) * clampedProgress,
                y = start.y + (end.y - start.y) * clampedProgress,
                z = start.z + (end.z - start.z) * clampedProgress,
                w = start.w + (end.w - start.w) * clampedProgress,
            ).normalized()
        }
        val angle = acos(dot.coerceIn(-1.0, 1.0))
        val denominator = sin(angle)
        val startWeight = sin((1.0 - clampedProgress) * angle) / denominator
        val endWeight = sin(clampedProgress * angle) / denominator
        return DiceAnimationQuaternion(
            x = start.x * startWeight + end.x * endWeight,
            y = start.y * startWeight + end.y * endWeight,
            z = start.z * startWeight + end.z * endWeight,
            w = start.w * startWeight + end.w * endWeight,
        ).normalized()
    }

    companion object {
        /** 不產生額外旋轉的 identity quaternion。 */
        val IDENTITY: DiceAnimationQuaternion = DiceAnimationQuaternion(0.0, 0.0, 0.0, 1.0)

        /**
         * 建立繞指定軸旋轉指定弧度的 quaternion。
         *
         * @param axis 旋轉軸；計算前會先正規化。
         * @param angleRadians 旋轉角度，單位為弧度。
         * @return 表示指定旋轉的正規化 quaternion。
         */
        fun fromAxisAngle(axis: DiceAnimationVector, angleRadians: Double): DiceAnimationQuaternion {
            val normalizedAxis = axis.normalized()
            val halfAngle = angleRadians / 2.0
            val scale = sin(halfAngle)
            return DiceAnimationQuaternion(
                x = normalizedAxis.x * scale,
                y = normalizedAxis.y * scale,
                z = normalizedAxis.z * scale,
                w = cos(halfAngle),
            ).normalized()
        }

        /** 接近相同方向時改用線性插值，避免除以接近零的數值。 */
        private const val LINEAR_INTERPOLATION_THRESHOLD = 0.9995
    }
}
