package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import kotlin.math.PI
import kotlin.math.sin

/** 流局與胡牌共用的排行榜落定視覺關鍵影格。 */
object SettlementRankingSettledEffect {
    /** 名次落定動畫的總 tick 長度。 */
    const val DURATION_TICKS = 14.0

    /**
     * 只為名次確實改變的列產生效果；時間軸外回傳無效果狀態。
     *
     * @param elapsed 目前演出經過的 tick。
     * @param settledTick 名次落定效果的起始 tick。
     * @param rankChanged 該列名次是否有變化。
     * @return 此時間點各項縮放、透明度與掃光進度；不活動時為 [State.NONE]。
     */
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

    /**
     * 在線性區間內插兩個縮放倍率。
     *
     * @param start 起始倍率。
     * @param end 結束倍率。
     * @param progress 內插進度。
     * @return 內插後的倍率。
     */
    private fun lerp(start: Float, end: Float, progress: Float): Float = start + (end - start) * progress

    /**
     * 一列名次落定動畫在單一時間點的視覺參數。
     *
     * @property active 是否處於效果時間窗內。
     * @property rowScale 整列縮放倍率。
     * @property rankScale 名次文字縮放倍率。
     * @property highlightAlpha 高亮底色透明度。
     * @property sweepProgress 掃光由左至右的進度。
     * @property rankWhiteness 名次文字由金色轉白的進度。
     */
    data class State(
        val active: Boolean,
        val rowScale: Float,
        val rankScale: Float,
        val highlightAlpha: Float,
        val sweepProgress: Float,
        val rankWhiteness: Float,
    ) {
        companion object {
            /** 不活動且不改變排名列外觀的狀態。 */
            val NONE = State(false, 1f, 1f, 0f, 0f, 0f)
        }
    }
}
