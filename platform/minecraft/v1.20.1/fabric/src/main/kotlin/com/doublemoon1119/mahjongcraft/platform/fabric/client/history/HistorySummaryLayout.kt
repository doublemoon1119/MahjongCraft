package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.VerticalScrollbar

/**
 * 牌譜摘要與其他一頁捲動內容畫面的版面：標題下方的捲動內容區與底部四格。
 *
 * 內容寬度、底部四格與捲軸位置沿用 [HistoryPageFrame]。
 *
 * @property frame 內容寬度、底部四格與捲軸位置。
 * @property contentTop 捲動內容上界。
 */
internal data class HistorySummaryLayout(
    val frame: HistoryPageFrame,
    val contentTop: Int,
) {
    /** 捲動內容下界。 */
    val contentBottom: Int = (frame.footerTop - HistoryPageFrame.FOOTER_GAP).coerceAtLeast(contentTop)

    /** 底部按鈕列上界。 */
    val footerTop: Int get() = frame.footerTop

    /** 內容左界。 */
    val left: Int get() = frame.contentLeft

    /** 內容右界。 */
    val right: Int get() = frame.contentRight

    /** 內容可視高度。 */
    val viewportHeight: Int get() = (contentBottom - contentTop).coerceAtLeast(1)

    /**
     * 判斷游標是否位於內容區內的一列，排除裁切區與捲軸。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param rowTop 捲動後的列上界。
     * @param rowHeight 列高度。
     * @return 是否指向這一列的可見部分。
     */
    fun containsRow(mouseX: Double, mouseY: Double, rowTop: Int, rowHeight: Int): Boolean = mouseX >= left &&
        mouseX < right &&
        mouseY >= contentTop &&
        mouseY < contentBottom &&
        mouseY >= rowTop &&
        mouseY < rowTop + rowHeight

    /**
     * 判斷游標是否位於內容區的可見範圍內。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 位於內容區內時為 true。
     */
    fun containsContent(mouseX: Double, mouseY: Double): Boolean = mouseX >= left && mouseX < right && mouseY >= contentTop && mouseY < contentBottom

    /** 捲軸範圍。 */
    fun scrollbarBounds(): HistoryScreenLayout.Bounds = frame.scrollbarBounds(top = contentTop, bottom = contentBottom)

    /**
     * 判斷游標是否落在捲軸的命中範圍內。
     *
     * @param mouseX 游標的水平座標。
     * @param mouseY 游標的垂直座標。
     * @return 位於命中範圍內時為 true。
     */
    fun isOverScrollbar(mouseX: Double, mouseY: Double): Boolean = frame.isOverScrollbar(mouseX = mouseX, mouseY = mouseY, top = contentTop, bottom = contentBottom)

    /**
     * 依內容高度建立像素捲軸。
     *
     * @param contentHeight 捲動內容總高度。
     * @param scrollOffset 目前捲動偏移。
     * @return 捲軸幾何。
     */
    fun scrollbar(contentHeight: Int, scrollOffset: Double): ScrollbarLayout = ScrollbarLayout(
        trackTop = contentTop,
        trackBottom = contentBottom,
        itemCount = contentHeight.coerceAtLeast(0),
        visibleItemCount = viewportHeight,
        scrollIndex = clampScroll(scrollOffset, contentHeight).toInt(),
        minimumThumbHeight = VerticalScrollbar.MINIMUM_THUMB_HEIGHT.coerceAtMost(viewportHeight),
    )

    /**
     * 保留滑塊內抓取位置，補償繪製時整數像素取整造成的偏移。
     *
     * @param contentHeight 內容總高度。
     * @param scrollOffset 目前捲動偏移。
     * @param mouseY 按下時的游標垂直座標。
     * @return 滑塊內的連續座標偏移；點擊軌道空白處時使用滑塊中央。
     */
    fun grabOffset(contentHeight: Int, scrollOffset: Double, mouseY: Double): Double {
        val bar = scrollbar(contentHeight, scrollOffset)
        if (bar.maximumScroll == 0 || mouseY < bar.thumbTop || mouseY >= bar.thumbTop + bar.thumbHeight) return bar.grabOffset(mouseY)
        val continuousTop = bar.trackTop + (bar.trackHeight - bar.thumbHeight) * clampScroll(scrollOffset, contentHeight) / bar.maximumScroll
        return mouseY - continuousTop
    }

    /**
     * 限制內容捲動偏移。
     *
     * @param offset 欲套用的偏移。
     * @param contentHeight 內容總高度。
     * @return 合法偏移。
     */
    fun clampScroll(offset: Double, contentHeight: Int): Double = offset.coerceIn(0.0, maximumScroll(contentHeight).toDouble())

    /**
     * 計算內容可捲動的最大偏移。
     *
     * @param contentHeight 內容總高度。
     * @return 最大偏移。
     */
    fun maximumScroll(contentHeight: Int): Int = (contentHeight - viewportHeight).coerceAtLeast(0)

    /**
     * 取得底部按鈕列中一格的範圍。
     *
     * @param index 由左至右的順序，從 0 開始。
     * @return 這一格的範圍。
     */
    fun footerSlot(index: Int): HistoryScreenLayout.Bounds = frame.footerSlot(index)

    /** 版面建立入口。 */
    companion object {
        /**
         * 依畫面尺寸建立版面；內容從標題下方開始。
         *
         * @param width 畫面寬度。
         * @param height 畫面高度。
         * @return 對應尺寸的版面。
         */
        fun measure(width: Int, height: Int): HistorySummaryLayout {
            val frame = HistoryPageFrame(windowWidth = width, windowHeight = height)
            return HistorySummaryLayout(frame = frame, contentTop = FormScreenLayout.TITLE_BOTTOM.coerceAtMost(frame.footerTop))
        }
    }
}
