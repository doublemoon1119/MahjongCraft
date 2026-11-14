package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** 內建機器人的出牌揮手節奏，採原版玩家平視時的右臂旋轉與正常揮手週期，不帶動牌體。 */
internal object MahjongBotArmAnimation {
    /** 原版玩家沒有加減速效果時的揮手 tick 數。 */
    const val DURATION_TICKS: Int = 6

    /** 原版右臂的主要前擺幅度。 */
    private const val FORWARD_RADIANS: Double = 1.2

    /** 原版頭部水平時的前擺補償。 */
    private const val LEVEL_HEAD_COMPENSATION: Double = 0.7 * 0.75

    /** 原版右臂的水平旋轉，換算至直立牌體的座標軸。 */
    private const val YAW_RADIANS: Double = -0.6

    /** 原版右臂向內收手的旋轉幅度。 */
    private const val ROLL_RADIANS: Double = -0.4

    /** 弧度換算為角度。 */
    private const val DEGREES_PER_RADIAN = (180.0 / PI).toFloat()

    /**
     * 原版右臂的水平轉向；只旋轉手臂，不扭動牌身。
     *
     * @param progress 本次揮手進度。
     * @return 手臂水平角度，無有效動作時為零。
     */
    fun yawDegrees(progress: Float): Float {
        if (!progress.isFinite() || progress <= 0f || progress >= 1f) return 0f
        return (sin(sqrt(progress.toDouble()) * 2.0 * PI) * YAW_RADIANS).toFloat() * DEGREES_PER_RADIAN
    }

    /**
     * 依原版右臂的角度向身體內側收手，再回到原位。
     *
     * @param progress 本次揮手進度。
     * @return 手臂內收角度，無有效動作時為零。
     */
    fun rollDegrees(progress: Float): Float {
        if (!progress.isFinite() || progress <= 0f || progress >= 1f) return 0f
        return (sin(progress * PI) * ROLL_RADIANS).toFloat() * DEGREES_PER_RADIAN
    }

    /**
     * 使用原版手臂的四次方緩動與平視補償，配合水平轉向及內收。
     *
     * @param progress 本次揮手進度。
     * @return 向前揮動的角度；動作之外或無效進度回傳零。
     */
    fun swingDegrees(progress: Float): Float {
        if (!progress.isFinite() || progress <= 0f || progress >= 1f) return 0f
        val remaining = 1f - progress
        val squared = remaining * remaining
        val eased = 1f - squared * squared
        val forward = sin(eased * PI) * FORWARD_RADIANS
        val compensation = sin(progress * PI) * LEVEL_HEAD_COMPENSATION
        return (forward + compensation).toFloat() * DEGREES_PER_RADIAN
    }
}
