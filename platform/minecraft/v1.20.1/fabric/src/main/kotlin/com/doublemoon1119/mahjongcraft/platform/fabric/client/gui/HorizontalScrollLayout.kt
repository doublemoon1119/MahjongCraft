package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import kotlin.math.roundToInt

/**
 * 水平 scrollbar thumb 的邊界。
 *
 * @property left 左邊界。
 * @property right 右邊界。
 */
internal data class HorizontalScrollThumb(
    val left: Int,
    val right: Int,
) {
    /** 寬度。 */
    val width: Int
        get() = right - left
}

/**
 * 連續像素的水平捲動幾何；統一 thumb 大小與位置、拖曳與滾輪的換算。
 *
 * 跟 [ScrollState]／[ScrollbarLayout] 是兩套不同模型：那一套是垂直、以項目 index 為單位一次捲一列，
 * 這一套是水平、以像素連續捲動，兩者的邊界與抓取點語意都不同，因此不共用。
 *
 * @property viewportLeft 可見區域左界。
 * @property viewportWidth 可見區域寬度。
 * @property contentWidth 內容總寬度。
 * @property minimumThumbWidth thumb 最小寬度。
 */
internal data class HorizontalScrollLayout(
    val viewportLeft: Int,
    val viewportWidth: Int,
    val contentWidth: Int,
    val minimumThumbWidth: Int,
) {
    /** 最大捲動量；內容沒有超出可見寬度時為零。 */
    val maximumScroll: Double = (contentWidth - viewportWidth).coerceAtLeast(0).toDouble()

    /**
     * 依可見比例與目前捲動量計算 thumb 邊界。
     *
     * 高 GUI scale 搭配小解析度時 [viewportWidth] 可能比 [minimumThumbWidth] 還窄，此時最小寬度本身必須
     * 先讓給可見寬度，否則下界會大於上界。
     */
    fun thumb(scroll: Double): HorizontalScrollThumb {
        val thumbWidth = if (maximumScroll <= 0.0 || contentWidth <= 0) {
            viewportWidth
        } else {
            (viewportWidth.toDouble() * viewportWidth / contentWidth)
                .roundToInt()
                .coerceIn(minimumThumbWidth.coerceAtMost(viewportWidth), viewportWidth)
        }
        val travel = viewportWidth - thumbWidth
        val left = viewportLeft + if (maximumScroll <= 0.0) 0 else (scroll / maximumScroll * travel).roundToInt()
        return HorizontalScrollThumb(left, left + thumbWidth)
    }

    /** 將 thumb 左界換算回捲動量，供直接點擊軌道或拖曳使用。 */
    fun scrollFromThumbLeft(thumbLeft: Double, scroll: Double = 0.0): Double {
        val travel = (viewportWidth - thumb(scroll).width).coerceAtLeast(1)
        val relative = (thumbLeft - viewportLeft).coerceIn(0.0, travel.toDouble())
        return relative / travel * maximumScroll
    }

    /** 依拖曳的游標位移計算新的捲動量。 */
    fun scrollFromDrag(startScroll: Double, pointerDelta: Double): Double {
        val travel = (viewportWidth - thumb(startScroll).width).coerceAtLeast(1)
        return (startScroll + pointerDelta / travel * maximumScroll).coerceIn(0.0, maximumScroll)
    }

    /** 依滾輪量與該畫面的步進距離計算新的捲動量。 */
    fun scrollFromWheel(currentScroll: Double, amount: Double, step: Double): Double = (currentScroll - amount * step).coerceIn(0.0, maximumScroll)
}
