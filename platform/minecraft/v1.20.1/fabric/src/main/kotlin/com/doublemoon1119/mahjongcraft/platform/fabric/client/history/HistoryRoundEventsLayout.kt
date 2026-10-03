package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollbarLayout

/** 單局事件畫面的固定標題、事件內容與底列幾何。
 *
 * @property contentTop 捲動內容上界。
 * @property contentBottom 捲動內容下界。
 * @property footerTop 固定底列上界。
 * @property left 內容左界。
 * @property right 內容右界。
 */
internal data class HistoryRoundEventsLayout(
    val contentTop: Int,
    val contentBottom: Int,
    val footerTop: Int,
    val left: Int,
    val right: Int,
) {
    /** 保留右側捲軸後可供卡片使用的右界。 */
    val cardRight: Int get() = (right - 10).coerceAtLeast(left)

    /** 保留捲軸後卡片可用寬度。 */
    val contentWidth: Int get() = (cardRight - left).coerceAtLeast(0)

    /** 可視內容高度。 */
    val viewportHeight: Int get() = (contentBottom - contentTop).coerceAtLeast(1)

    /** 將捲動位置限制在有效範圍。
     * @param offset 欲套用的捲動位置。
     * @param contentHeight 內容總高度。
     * @return 限制後的位置。
     */
    fun clampScroll(offset: Double, contentHeight: Int): Double = offset.coerceIn(0.0, maximumScroll(contentHeight).toDouble())

    /** 計算內容最大捲動位置。
     * @param contentHeight 內容總高度。
     * @return 最大捲動位置。
     */
    fun maximumScroll(contentHeight: Int): Int = (contentHeight - viewportHeight).coerceAtLeast(0)

    /** 建立捲軸幾何。
     * @param contentHeight 內容總高度。
     * @param offset 目前捲動位置。
     * @return 捲軸幾何。
     */
    fun scrollbar(contentHeight: Int, offset: Double): ScrollbarLayout = ScrollbarLayout(
        trackTop = contentTop,
        trackBottom = contentBottom,
        itemCount = contentHeight.coerceAtLeast(0),
        visibleItemCount = viewportHeight,
        scrollIndex = clampScroll(offset, contentHeight).toInt(),
        minimumThumbHeight = 8.coerceAtMost(viewportHeight),
    )

    /** 捲軸界線。 */
    fun scrollbarBounds(): HistoryScreenLayout.Bounds = HistoryScreenLayout.Bounds(
        right - 6,
        contentTop,
        6,
        (contentBottom - contentTop).coerceAtLeast(0),
    )

    /** 判斷游標是否位於內容區內指定卡片，並排除捲軸與裁切範圍。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param top 卡片上界。
     * @param height 卡片高度。
     * @return 是否指向卡片可見區域。
     */
    fun containsCard(mouseX: Double, mouseY: Double, top: Int, height: Int): Boolean = mouseX >= left &&
        mouseX < cardRight &&
        mouseY >= contentTop &&
        mouseY < contentBottom &&
        mouseY >= top &&
        mouseY < top + height

    /** 計算單列可容納的牌數。
     * @param tileWidth 單張牌寬度。
     * @param tileStep 牌面左界之間的距離。
     * @return 每列牌數。
     */
    fun tilesPerRow(tileWidth: Int = DEFAULT_TILE_WIDTH, tileStep: Int = DEFAULT_TILE_STEP): Int = ((contentWidth - TILE_ROW_PADDING - tileWidth).coerceAtLeast(0) / tileStep + 1).coerceAtLeast(1)

    /** 計算牌面需要的列數。
     * @param count 牌面數量。
     * @param tileWidth 單張牌寬度。
     * @param tileStep 牌面左界之間的距離。
     * @return 牌面列數。
     */
    fun tileRowCount(count: Int, tileWidth: Int = DEFAULT_TILE_WIDTH, tileStep: Int = DEFAULT_TILE_STEP): Int = if (count <= 0) 0 else (count + tilesPerRow(tileWidth, tileStep) - 1) / tilesPerRow(tileWidth, tileStep)

    /** 依寬度配置返回、上一頁、頁碼、下一頁四個底列槽位。
     * @param width 畫面寬度。
     * @return 依控制項順序排列的槽位；頁碼保留兩列文字高度。
     */
    fun footerBounds(width: Int): List<HistoryScreenLayout.Bounds> {
        val gap = 4
        if (width < NARROW_WIDTH) {
            val topWidth = ((width - 16 - gap * 2) / 3).coerceAtLeast(1)
            return listOf(
                HistoryScreenLayout.Bounds(8, footerTop + FOOTER_ROW_HEIGHT + gap, width - 16, 20),
                HistoryScreenLayout.Bounds(8, footerTop, topWidth, 20),
                HistoryScreenLayout.Bounds(8 + topWidth + gap, footerTop, topWidth, FOOTER_ROW_HEIGHT),
                HistoryScreenLayout.Bounds(8 + (topWidth + gap) * 2, footerTop, topWidth, 20),
            )
        }
        val buttonWidth = ((width - 24 - gap * 3) / 4).coerceAtLeast(1)
        val total = buttonWidth * 4 + gap * 3
        val left = ((width - total) / 2).coerceAtLeast(8)
        return List(4) { index -> HistoryScreenLayout.Bounds(left + index * (buttonWidth + gap), footerTop, buttonWidth, if (index == 2) FOOTER_ROW_HEIGHT else 20) }
    }

    /** 保留捲軸滑塊內的抓取位置。
     * @param contentHeight 內容總高度。
     * @param offset 目前捲動位置。
     * @param mouseY 游標垂直位置。
     * @return 滑塊內的相對位置。
     */
    fun grabOffset(contentHeight: Int, offset: Double, mouseY: Double): Double {
        val bar = scrollbar(contentHeight, offset)
        if (bar.maximumScroll == 0 || mouseY < bar.thumbTop || mouseY >= bar.thumbTop + bar.thumbHeight) return bar.grabOffset(mouseY)
        val top = bar.trackTop + (bar.trackHeight - bar.thumbHeight) * clampScroll(offset, contentHeight) / bar.maximumScroll
        return mouseY - top
    }

    /** 版面測量與共用尺寸。 */
    companion object {
        /** 依畫面大小建立幾何，固定底列不與內容重疊。
         * @param width 畫面寬度。
         * @param height 畫面高度。
         * @param headerHeight 換行後固定標題區高度。
         * @return 對應尺寸的版面。
         */
        fun measure(width: Int, height: Int, headerHeight: Int = 48): HistoryRoundEventsLayout {
            val rows = if (width < NARROW_WIDTH) 2 else 1
            val footer = (height - rows * FOOTER_ROW_HEIGHT - 4).coerceAtLeast(0)
            val top = headerHeight.coerceAtMost(footer)
            val left = 10
            val right = (width - 10).coerceAtLeast(left + 6)
            return HistoryRoundEventsLayout(top, (footer - 4).coerceAtLeast(top), footer, left, right)
        }

        /** 使用雙列底部控制項的寬度界線。 */
        private const val NARROW_WIDTH = 360

        /** 底列及兩列頁碼文字所需高度。 */
        private const val FOOTER_ROW_HEIGHT = 24

        /** 牌面列左右內距總和。 */
        private const val TILE_ROW_PADDING = 24

        /** 預設牌面寬度。 */
        private const val DEFAULT_TILE_WIDTH = 18

        /** 預設牌面起點間距。 */
        private const val DEFAULT_TILE_STEP = 20
    }
}
