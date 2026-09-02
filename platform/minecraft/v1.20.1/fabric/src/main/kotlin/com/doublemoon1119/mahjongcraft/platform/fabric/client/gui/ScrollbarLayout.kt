package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import kotlin.math.roundToInt

/** 可測試的垂直 scrollbar 幾何；統一 thumb 繪製與拖曳座標系。 */
internal data class ScrollbarLayout(
    /** 軌道上界。 */
    val trackTop: Int,
    /** 軌道下界。 */
    val trackBottom: Int,
    /** 全部項目數。 */
    val itemCount: Int,
    /** 可見項目數。 */
    val visibleItemCount: Int,
    /** 目前第一個可見項目的 index。 */
    val scrollIndex: Int,
    /** Thumb 最小高度。 */
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

    /** 將按下位置換算為 thumb 內抓取偏移；軌道空白處以 thumb 中央為抓取點。 */
    fun grabOffset(mouseY: Double): Double = if (mouseY in thumbTop.toDouble()..(thumbTop + thumbHeight).toDouble()) {
        mouseY - thumbTop
    } else {
        thumbHeight / 2.0
    }

    /** 依游標與抓取偏移換算 scroll index。 */
    fun scrollIndexFor(mouseY: Double, grabOffset: Double): Int {
        if (maximumScroll == 0) return 0
        val travel = trackHeight - thumbHeight
        if (travel <= 0) return 0
        val thumbPosition = (mouseY - grabOffset - trackTop).coerceIn(0.0, travel.toDouble())
        return (thumbPosition / travel * maximumScroll).roundToInt().coerceIn(0, maximumScroll)
    }
}
