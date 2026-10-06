package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollbarLayout

/** 歷史摘要固定標題、捲動內容與底列的版面幾何。
 *
 * @property contentTop 捲動內容上界。
 * @property contentBottom 捲動內容下界。
 * @property footerTop 固定底列上界。
 * @property left 內容左界。
 * @property right 內容右界。
 */
internal data class HistorySummaryLayout(
    val contentTop: Int,
    val contentBottom: Int,
    val footerTop: Int,
    val left: Int,
    val right: Int,
) {
    /** 內容可視高度。 */
    val viewportHeight: Int get() = (contentBottom - contentTop).coerceAtLeast(1)

    /**
     * 判斷游標是否位於可見資訊列，排除裁切區與捲軸。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param rowLeft 資訊列左界。
     * @param rowTop 捲動後的資訊列上界。
     * @param rowHeight 資訊列高度。
     * @return 是否指向資訊列的可見部分。
     */
    fun containsContentRow(mouseX: Double, mouseY: Double, rowLeft: Int, rowTop: Int, rowHeight: Int): Boolean = mouseX >= rowLeft &&
        mouseX < right - 14 &&
        mouseY >= contentTop &&
        mouseY < contentBottom &&
        mouseY >= rowTop &&
        mouseY < rowTop + rowHeight

    /**
     * 從摘要換行後的可見局列取得欲開啟的局序號。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param scrollOffset 已限制範圍的內容捲動偏移。
     * @param links 各局每個換行列的點擊位置。
     * @return 可見局列的局序號；裁切區、列間隙或捲軸均為 null。
     */
    fun roundAt(mouseX: Double, mouseY: Double, scrollOffset: Double, links: List<RoundLink>): Int? = links.firstOrNull {
        containsContentRow(mouseX, mouseY, it.x, it.y - scrollOffset.toInt(), 11)
    }?.roundNumber

    /**
     * 同一局的一個已換行摘要列。
     *
     * @property x 列的左界。
     * @property y 未捲動的列上界。
     * @property roundNumber 該列所屬局序號。
     */
    data class RoundLink(
        val x: Int,
        val y: Int,
        val roundNumber: Int,
    )

    /** 捲軸界線，固定為可見的六像素寬。 */
    fun scrollbarBounds(): HistoryScreenLayout.Bounds = HistoryScreenLayout.Bounds(
        right - 6,
        contentTop,
        6,
        (contentBottom - contentTop).coerceAtLeast(0),
    )

    /** 依內容高度建立像素捲軸。
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
        minimumThumbHeight = 8.coerceAtMost(viewportHeight),
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

    /** 限制內容捲動偏移。
     *
     * @param offset 欲套用的偏移。
     * @param contentHeight 內容總高度。
     * @return 合法偏移。
     */
    fun clampScroll(offset: Double, contentHeight: Int): Double = offset.coerceIn(0.0, maximumScroll(contentHeight).toDouble())

    /** 計算內容可捲動的最大偏移。
     *
     * @param contentHeight 內容總高度。
     * @return 最大偏移。
     */
    fun maximumScroll(contentHeight: Int): Int = (contentHeight - viewportHeight).coerceAtLeast(0)

    /**
     * 底部按鈕位置：只有返回按鈕時置中；需要顯示重試按鈕時兩顆並排置中，返回在左。
     *
     * @param width 畫面寬度。
     * @param retryVisible 是否顯示重試按鈕。
     * @return 返回按鈕與重試按鈕的界線；不顯示重試時第二項為 null。
     */
    fun footerButtons(width: Int, retryVisible: Boolean): Pair<HistoryScreenLayout.Bounds, HistoryScreenLayout.Bounds?> {
        val buttonWidth = ((width - FOOTER_MARGIN * 2 - FOOTER_GAP) / 2).coerceAtLeast(1)
        if (!retryVisible) return HistoryScreenLayout.Bounds((width - buttonWidth) / 2, footerTop, buttonWidth, FOOTER_BUTTON_HEIGHT) to null
        val left = ((width - buttonWidth * 2 - FOOTER_GAP) / 2).coerceAtLeast(FOOTER_MARGIN)
        return HistoryScreenLayout.Bounds(left, footerTop, buttonWidth, FOOTER_BUTTON_HEIGHT) to
            HistoryScreenLayout.Bounds(left + buttonWidth + FOOTER_GAP, footerTop, buttonWidth, FOOTER_BUTTON_HEIGHT)
    }

    /** 摘要版面建立入口。 */
    companion object {
        /**
         * 依畫面尺寸建立摘要版面。
         *
         * @param width 畫面寬度。
         * @param height 畫面高度。
         * @return 對應尺寸的幾何資料。
         */
        fun measure(width: Int, height: Int): HistorySummaryLayout {
            val footer = (height - 28).coerceAtLeast(0)
            val top = 24.coerceAtMost(footer)
            val left = 10
            val right = (width - 10).coerceAtLeast(left + 6)
            return HistorySummaryLayout(top, (footer - 4).coerceAtLeast(top), footer, left, right)
        }

        /** 底部按鈕與畫面左右邊緣的最小距離。 */
        private const val FOOTER_MARGIN = 12

        /** 兩顆底部按鈕之間的距離。 */
        private const val FOOTER_GAP = 4

        /** 底部按鈕高度。 */
        private const val FOOTER_BUTTON_HEIGHT = 20
    }
}
