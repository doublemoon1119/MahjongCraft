package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import kotlin.math.roundToInt

/**
 * 垂直捲軸的軌道與滑塊位置，供繪製和拖曳使用同一套座標。
 *
 * @property trackTop 軌道上界的縱座標。
 * @property trackBottom 軌道下界的縱座標。
 * @property itemCount 全部項目數。
 * @property visibleItemCount 同時可見的項目數。
 * @property scrollIndex 第一個可見項目的索引。
 * @property minimumThumbHeight 滑塊最小高度；應介於 0 與軌道高度之間。
 */
internal data class ScrollbarLayout(
    val trackTop: Int,
    val trackBottom: Int,
    val itemCount: Int,
    val visibleItemCount: Int,
    val scrollIndex: Int,
    val minimumThumbHeight: Int,
) {
    /** 最大合法 scroll index。 */
    val maximumScroll: Int = (itemCount - visibleItemCount).coerceAtLeast(0)

    /** 軌道高度。 */
    val trackHeight: Int = (trackBottom - trackTop).coerceAtLeast(1)

    /** Thumb 高度。 */
    val thumbHeight: Int = if (maximumScroll == 0) {
        trackHeight
    } else {
        (trackHeight * visibleItemCount / itemCount).coerceIn(minimumThumbHeight, trackHeight)
    }

    /** Thumb 上界。 */
    val thumbTop: Int = if (maximumScroll == 0) {
        trackTop
    } else {
        trackTop + (trackHeight - thumbHeight) * scrollIndex.coerceIn(0, maximumScroll) / maximumScroll
    }

    /**
     * 將按下位置換算為滑塊內的抓取偏移；按在軌道空白處時，改以滑塊中央為抓取點。
     *
     * @param mouseY 游標的縱座標。
     * @return 抓取點距離滑塊上界的距離。
     */
    fun grabOffset(mouseY: Double): Double = if (mouseY in thumbTop.toDouble()..(thumbTop + thumbHeight).toDouble()) {
        mouseY - thumbTop
    } else {
        thumbHeight / 2.0
    }

    /**
     * 依游標與抓取偏移換算第一個可見項目的索引。
     *
     * @param mouseY 游標的縱座標。
     * @param grabOffset 抓取點距離滑塊上界的距離。
     * @return 合法範圍內的索引；沒有可捲動距離時為 0。
     */
    fun scrollIndexFor(mouseY: Double, grabOffset: Double): Int {
        if (maximumScroll == 0) return 0
        val travel = trackHeight - thumbHeight
        if (travel <= 0) return 0
        val thumbPosition = (mouseY - grabOffset - trackTop).coerceIn(0.0, travel.toDouble())
        return (thumbPosition / travel * maximumScroll).roundToInt().coerceIn(0, maximumScroll)
    }
}
