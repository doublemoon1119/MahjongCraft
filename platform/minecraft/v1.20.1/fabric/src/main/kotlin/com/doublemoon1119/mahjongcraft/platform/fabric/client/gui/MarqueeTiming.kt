package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import kotlin.math.roundToInt

/** 跑馬燈的共用節奏：開頭停頓後移到底，停頓後移回開頭，再停頓並循環。 */
internal object MarqueeTiming {
    /** 開始顯示後第一次移動前的停頓。 */
    internal const val START_PAUSE_MILLIS = 350L

    /** 移到任一端後的停頓。 */
    internal const val END_PAUSE_MILLIS = 450L

    /** 每移動一像素花費的時間。 */
    internal const val MILLIS_PER_PIXEL = 35L

    /**
     * 取得目前的位移。
     *
     * @param elapsedMillis 從開始顯示起經過的毫秒數。
     * @param overflow 超出可視範圍的像素數；不大於 0 時不移動。
     * @return 介於 0 與 [overflow] 之間的位移。
     */
    fun offset(elapsedMillis: Long, overflow: Int): Int {
        if (overflow <= 0 || elapsedMillis <= START_PAUSE_MILLIS) return 0
        val travelMillis = (overflow * MILLIS_PER_PIXEL).coerceAtLeast(1L)
        val cycleMillis = travelMillis * 2 + END_PAUSE_MILLIS * 2
        val cyclePosition = (elapsedMillis - START_PAUSE_MILLIS) % cycleMillis
        return when {
            cyclePosition < travelMillis -> (cyclePosition.toDouble() / travelMillis * overflow).roundToInt()
            cyclePosition < travelMillis + END_PAUSE_MILLIS -> overflow
            cyclePosition < travelMillis * 2 + END_PAUSE_MILLIS ->
                (overflow - (cyclePosition - travelMillis - END_PAUSE_MILLIS).toDouble() / travelMillis * overflow).roundToInt()
            else -> 0
        }
    }
}
