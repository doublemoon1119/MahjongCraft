package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationCinematicTimeline
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationShowcaseEntity
import kotlin.math.PI
import kotlin.math.sin

/**
 * 役滿 showcase 隨時間變化的曲線。
 *
 * 所有函式只吃經過的 tick 數（或已換算好的進度），回傳縮放、可見度或位移；不碰 entity 或繪製。
 */
object ShowcaseTimelineCurves {
    /** 鞘翅與煙火開始具現的 tick。 */
    const val ELYTRA_APPEAR_TICK: Double = 32.0

    /** 鞘翅具現完成、開始展開的 tick。 */
    const val ELYTRA_OPEN_TICK: Double = 40.0

    /** 鞘翅開始收攏淡出的 tick。 */
    const val ELYTRA_FADE_START_TICK: Double = 260.0

    /** 鞘翅完全消失的 tick。 */
    const val ELYTRA_FADE_END_TICK: Double = 276.0

    /** 全部起飛裝備完全消失的 tick。 */
    const val EQUIPMENT_FADE_END_TICK: Double = 276.0

    /** 展示中卡片上下晃動的振幅（方塊）。 */
    const val BOB_HEIGHT: Double = 0.025

    /** 展示中卡片上下晃動的角速度（弧度／tick）。 */
    const val BOB_SPEED: Double = 0.22

    /** 線性內插。 */
    fun lerp(
        start: Double,
        end: Double,
        progress: Double,
    ): Double = start + (end - start) * progress

    /** 兩端斜率為 0 的平滑曲線；輸入應在 0..1。 */
    fun smoothStep(value: Double): Double = value * value * (3.0 - 2.0 * value)

    /** 起步快、收尾慢的二次曲線；輸入應在 0..1。 */
    fun easeOut(value: Double): Double = 1.0 - (1.0 - value) * (1.0 - value)

    /** 收尾淡出的縮放係數：[fadeStart] 之前為 1，之後在 [WinCelebrationShowcaseEntity.FADE_OUT_TICKS] 內平滑降到 0。 */
    fun fadeScale(
        elapsed: Double,
        fadeStart: Double,
    ): Double = if (elapsed < fadeStart) {
        1.0
    } else {
        1.0 - smoothStep(((elapsed - fadeStart) / WinCelebrationShowcaseEntity.FADE_OUT_TICKS).coerceIn(0.0, 1.0))
    }

    /** 起飛裝備的可見度：抵達後在 [ELYTRA_FADE_START_TICK]～[EQUIPMENT_FADE_END_TICK] 間平滑降到 0。 */
    fun equipmentVisibility(elapsed: Double): Double = when {
        elapsed < ELYTRA_FADE_START_TICK -> 1.0
        elapsed < EQUIPMENT_FADE_END_TICK -> 1.0 - smoothStep((elapsed - ELYTRA_FADE_START_TICK) / (EQUIPMENT_FADE_END_TICK - ELYTRA_FADE_START_TICK))
        else -> 0.0
    }

    /** 起飛裝備由小到大具現的進度：[ELYTRA_APPEAR_TICK]～[ELYTRA_OPEN_TICK] 間由 0 升到 1。 */
    fun equipmentAppearance(elapsed: Double): Double = easeOut(
        ((elapsed - ELYTRA_APPEAR_TICK) / (ELYTRA_OPEN_TICK - ELYTRA_APPEAR_TICK)).coerceIn(0.0, 1.0),
    )

    /** 和牌張的垂直晃動；和牌張本體與接觸波紋共用這一個來源。 */
    fun winningTileBobOffset(
        elapsed: Double,
        fade: Double,
    ): Double = sin(elapsed * BOB_SPEED) * BOB_HEIGHT * fade

    /**
     * 距離最近一次晃動最低點的 tick 數。
     *
     * 範圍為半個週期的正負值：負值表示正在下降、尚未到達最低點；正值表示已過最低點、正在上升。
     */
    fun winningTileTicksFromBottom(elapsed: Double): Double {
        val twoPi = PI * 2.0
        val rawPhase = elapsed * BOB_SPEED - PI * 1.5
        val wrappedPhase = ((rawPhase + PI) % twoPi + twoPi) % twoPi - PI
        return wrappedPhase / BOB_SPEED
    }

    /**
     * 引信燃燒進度為 [fuse]（0..1）時 TNT 是否閃白。
     *
     * 越接近爆炸，閃爍週期越短、每次亮起的時間占比越高。
     */
    fun tntFlashVisible(fuse: Double): Boolean {
        val cycle = 8.0 - fuse * 6.5
        val phase = (fuse * (WinCelebrationCinematicTimeline.EXPLOSION_START - WinCelebrationCinematicTimeline.IGNITION_START)) % cycle
        return phase < lerp(1.0, cycle * 0.72, fuse)
    }
}
