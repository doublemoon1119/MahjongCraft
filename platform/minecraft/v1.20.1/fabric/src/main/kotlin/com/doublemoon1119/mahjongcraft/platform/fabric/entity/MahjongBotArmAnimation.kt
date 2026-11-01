package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import kotlin.math.PI
import kotlin.math.sin

/** 內建機器人的出牌揮手節奏，採原版玩家正常揮手週期與手臂主要緩動，不帶動牌體。 */
internal object MahjongBotArmAnimation {
    /** 原版玩家沒有加減速效果時的揮手 tick 數。 */
    const val DURATION_TICKS: Int = 6

    /** 保持粗短手臂的小幅動作範圍。 */
    private const val SWING_ANGLE: Float = 55f

    /**
     * 使用原版手臂的四次方緩動：快速揮出，再逐漸收回。
     *
     * @param progress 本次揮手進度。
     * @return 向前揮動的角度；動作之外或無效進度回傳零。
     */
    fun swingDegrees(progress: Float): Float {
        if (!progress.isFinite() || progress <= 0f || progress >= 1f) return 0f
        val remaining = 1f - progress
        val squared = remaining * remaining
        val eased = 1f - squared * squared
        return sin(eased * PI).toFloat() * SWING_ANGLE
    }
}
