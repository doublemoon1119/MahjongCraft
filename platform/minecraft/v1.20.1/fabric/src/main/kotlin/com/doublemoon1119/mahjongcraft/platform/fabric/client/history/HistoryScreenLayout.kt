package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollbarLayout

/** 歷史畫面固定列與捲動內容區的幾何資料。
 *
 * @property toolbarTop 工具列起點。
 * @property contentTop 捲動內容起點。
 * @property contentBottom 捲動內容底端。
 * @property footerTop 固定底列起點。
 * @property contentWidth 內容區可用寬度。
 */
internal data class HistoryScreenLayout(
    val toolbarTop: Int,
    val contentTop: Int,
    val contentBottom: Int,
    val footerTop: Int,
    val contentWidth: Int,
) {
    /** 將捲動偏移限制於內容可見範圍，供輸入與繪製前共用。
     *
     * @param offset 要套用的像素偏移。
     * @param contentHeight 清單內容總高度。
     * @return 不超過頂端及底端的偏移。
     */
    fun clampScroll(offset: Double, contentHeight: Int): Double = offset.coerceIn(0.0, maximumScroll(contentHeight).toDouble())

    /** 計算清單內容超出可視區的高度。
     *
     * @param contentHeight 清單內容總高度。
     * @return 允許的最大像素偏移。
     */
    fun maximumScroll(contentHeight: Int): Int = (contentHeight - (contentBottom - contentTop).coerceAtLeast(1)).coerceAtLeast(0)

    /** 計算內容區右側捲軸的界線，並限制在內容裁切範圍內。
     *
     * @param width 畫面可用寬度。
     * @return 捲軸軌道的界線。
     */
    fun scrollbarBounds(width: Int): Bounds {
        val scissorLeft = 8
        val scissorRight = (width - 8).coerceAtLeast(scissorLeft)
        val barWidth = 6.coerceAtMost((scissorRight - scissorLeft).coerceAtLeast(0))
        val x = (scissorRight - barWidth).coerceAtLeast(scissorLeft)
        return Bounds(x, contentTop, barWidth, (contentBottom - contentTop).coerceAtLeast(0))
    }

    /** 計算單張歷史卡片的背景界線，為捲軸保留右側空間。
     *
     * @param width 畫面可用寬度。
     * @param top 卡片上側座標。
     * @param height 卡片完整高度。
     * @param scrollbarVisible 是否顯示右側捲軸。
     * @return 卡片背景的界線。
     */
    fun cardBounds(width: Int, top: Int, height: Int, scrollbarVisible: Boolean): Bounds {
        val left = 10
        val right = width - if (scrollbarVisible) 20 else 10
        return Bounds(left, top, (right - left).coerceAtLeast(0), (height - 2).coerceAtLeast(0))
    }

    /** 依內容像素高度建立歷史列表捲軸幾何。
     *
     * @param contentHeight 清單內容總高度。
     * @param scrollOffset 目前像素捲動偏移。
     * @return 捲軸軌道、thumb 與目前位置的幾何資料。
     */
    fun scrollbar(contentHeight: Int, scrollOffset: Double): ScrollbarLayout {
        val viewportHeight = (contentBottom - contentTop).coerceAtLeast(1)
        val trackHeight = (contentBottom - contentTop).coerceAtLeast(1)
        return ScrollbarLayout(
            trackTop = contentTop,
            trackBottom = contentBottom,
            itemCount = contentHeight,
            visibleItemCount = viewportHeight,
            scrollIndex = clampScroll(scrollOffset, contentHeight).toInt(),
            minimumThumbHeight = 8.coerceAtMost(trackHeight),
        )
    }

    /** 可重用的矩形控制項界線。
     *
     * @property x 左側座標。
     * @property y 上側座標。
     * @property width 寬度。
     * @property height 高度。
     */
    data class Bounds(val x: Int, val y: Int, val width: Int, val height: Int)

    /** 依可用寬度配置工具列，確保每個控制項都在畫面內。
     *
     * @param width 畫面可用寬度。
     * @return 工具列控制項的界線。
     */
    fun toolbarBounds(width: Int): List<Bounds> {
        if (width < 450) {
            val gap = 4
            val buttonWidth = ((width - 16 - gap * 2) / 3).coerceAtLeast(36)
            return List(5) { index ->
                val row = index / 3
                val column = index % 3
                Bounds(8 + column * (buttonWidth + gap), 12 + row * 24, buttonWidth, 20)
            }
        }
        val gap = 4
        val count = 5
        val buttonWidth = ((width - 16 - gap * (count - 1)) / count).coerceAtLeast(40)
        return List(count) { index -> Bounds(8 + index * (buttonWidth + gap), 12, buttonWidth, 20) }
    }

    /** 依可用寬度配置固定底列，避免分頁按鈕與關閉按鈕重疊。
     *
     * @param width 畫面可用寬度。
     * @param footerTop 底列的垂直起點。
     * @return 底列控制項的界線。
     */
    fun footerBounds(width: Int, footerTop: Int): List<Bounds> {
        val gap = 4
        val buttonWidth = ((width - 24 - gap * 3) / 4).coerceAtLeast(1)
        val total = buttonWidth * 4 + gap * 3
        val left = ((width - total) / 2).coerceAtLeast(8)
        return List(4) { index -> Bounds(left + index * (buttonWidth + gap), footerTop, buttonWidth, 20) }
    }

    /** 內容區是否有正高度。 */
    val hasContent: Boolean get() = contentBottom > contentTop

    /** 計算窄視窗仍不重疊的版面。 */
    companion object {
        /** 依畫面尺寸建立版面；最小高度仍保留固定底列。
         *
         * @param width 畫面寬度。
         * @param height 畫面高度。
         * @return 對應尺寸的版面幾何資料。
         */
        fun measure(width: Int, height: Int): HistoryScreenLayout {
            val footer = (height - 28).coerceAtLeast(0)
            val toolbar = if (width < 450) 72 else 48
            val top = toolbar.coerceAtMost(footer)
            return HistoryScreenLayout(top - 1, top, (footer - 4).coerceAtLeast(top), footer, (width - 24).coerceAtLeast(0))
        }
    }
}
