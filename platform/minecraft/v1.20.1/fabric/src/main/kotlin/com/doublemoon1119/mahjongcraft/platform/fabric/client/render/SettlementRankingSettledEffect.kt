package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import kotlin.math.PI
import kotlin.math.sin

/** 流局與胡牌共用的排行榜落定視覺關鍵影格。 */
object SettlementRankingSettledEffect {
    const val DURATION_TICKS = 14.0

    /** 只為名次確實改變的列產生效果；時間軸外回傳無效果狀態。 */
    fun resolve(elapsed: Double, settledTick: Double, rankChanged: Boolean): State {
        if (!rankChanged) return State.NONE
        val progress = ((elapsed - settledTick) / DURATION_TICKS).coerceIn(0.0, 1.0).toFloat()
        if (elapsed < settledTick || elapsed >= settledTick + DURATION_TICKS) return State.NONE
        val rowScale = when {
            progress < 0.35f -> lerp(1f, 1.025f, progress / 0.35f)
            progress < 0.65f -> lerp(1.025f, 0.99f, (progress - 0.35f) / 0.3f)
            else -> lerp(0.99f, 1f, (progress - 0.65f) / 0.35f)
        }
        val pulse = sin(PI * progress).toFloat().coerceAtLeast(0f)
        return State(
            active = true,
            rowScale = rowScale,
            rankScale = 1f + 0.25f * pulse,
            highlightAlpha = 0.2f * pulse,
            sweepProgress = progress,
            rankWhiteness = pulse,
        )
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float = start + (end - start) * progress

    data class State(
        val active: Boolean,
        val rowScale: Float,
        val rankScale: Float,
        val highlightAlpha: Float,
        val sweepProgress: Float,
        val rankWhiteness: Float,
    ) {
        companion object {
            val NONE = State(false, 1f, 1f, 0f, 0f, 0f)
        }
    }
}
