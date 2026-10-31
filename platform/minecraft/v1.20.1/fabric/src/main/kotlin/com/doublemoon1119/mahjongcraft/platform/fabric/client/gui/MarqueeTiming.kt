package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

/** 跑馬燈的共用節奏：開頭停頓後移到底，停頓後移回開頭，再停頓並循環。 */
internal object MarqueeTiming {
    /** 開始顯示後第一次移動前的停頓。 */
    internal val START_PAUSE: Duration = 350.milliseconds

    /** 移到任一端後的停頓。 */
    internal val END_PAUSE: Duration = 450.milliseconds

    /** 每移動一像素花費的時間。 */
    internal val TIME_PER_PIXEL: Duration = 35.milliseconds

    /**
     * 取得目前的位移。
     *
     * @param elapsed 從開始顯示起經過的時間。
     * @param overflow 超出可視範圍的像素數；不大於 0 時不移動。
     * @return 超出寬度為正時，回傳介於 0 與 [overflow] 之間的位移；否則為 0。
     */
    fun offset(elapsed: Duration, overflow: Int): Int {
        if (overflow <= 0 || elapsed <= START_PAUSE) return 0
        val travel = TIME_PER_PIXEL * overflow
        val cycle = travel * 2 + END_PAUSE * 2
        val position = ((elapsed - START_PAUSE).inWholeNanoseconds % cycle.inWholeNanoseconds).nanoseconds
        return when {
            position < travel -> (position / travel * overflow).roundToInt()
            position < travel + END_PAUSE -> overflow
            position < travel * 2 + END_PAUSE -> (overflow - (position - travel - END_PAUSE) / travel * overflow).roundToInt()
            else -> 0
        }
    }
}
