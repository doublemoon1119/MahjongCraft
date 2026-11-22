package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar

/**
 * 單局事件與局面畫面的版面：標題下方的固定區、捲動的卡片區與底部四格。
 *
 * 內容寬度、底部四格與捲軸位置沿用 [HistoryPageFrame]；卡片橫跨內容寬度。
 *
 * @property frame 內容寬度、底部四格與捲軸位置。
 * @property contentTop 捲動內容上界。
 */
internal data class HistoryRoundEventsLayout(
    val frame: HistoryPageFrame,
    val contentTop: Int,
) {
    /** 捲動內容下界。 */
    val contentBottom: Int = (frame.footerTop - HistoryPageFrame.FOOTER_GAP).coerceAtLeast(contentTop)

    /** 底部按鈕列上界。 */
    val footerTop: Int get() = frame.footerTop

    /** 內容左界。 */
    val left: Int get() = frame.contentLeft

    /** 內容右界，也是卡片右界。 */
    val right: Int get() = frame.contentRight

    /** 卡片可用寬度。 */
    val contentWidth: Int get() = frame.contentWidth

    /** 可視內容高度。 */
    val viewportHeight: Int get() = (contentBottom - contentTop).coerceAtLeast(1)

    /**
     * 將捲動位置限制在有效範圍。
     *
     * @param offset 欲套用的捲動位置。
     * @param contentHeight 內容總高度。
     * @return 限制後的位置。
     */
    fun clampScroll(offset: Double, contentHeight: Int): Double = offset.coerceIn(0.0, maximumScroll(contentHeight).toDouble())

    /**
     * 計算內容最大捲動位置。
     *
     * @param contentHeight 內容總高度。
     * @return 最大捲動位置。
     */
    fun maximumScroll(contentHeight: Int): Int = (contentHeight - viewportHeight).coerceAtLeast(0)

    /**
     * 建立捲軸幾何。
     *
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
        minimumThumbHeight = VerticalScrollbar.MINIMUM_THUMB_HEIGHT.coerceAtMost(viewportHeight),
    )

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
     * 判斷游標是否位於內容區內指定卡片，並排除捲軸與裁切範圍。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param top 卡片上界。
     * @param height 卡片高度。
     * @return 是否指向卡片可見區域。
     */
    fun containsCard(mouseX: Double, mouseY: Double, top: Int, height: Int): Boolean = mouseX >= left &&
        mouseX < right &&
        mouseY >= contentTop &&
        mouseY < contentBottom &&
        mouseY >= top &&
        mouseY < top + height

    /**
     * 計算單列可容納的牌數。
     *
     * @param tileWidth 單張牌寬度。
     * @param tileStep 牌面左界之間的距離。
     * @return 每列牌數。
     */
    fun tilesPerRow(tileWidth: Int = DEFAULT_TILE_WIDTH, tileStep: Int = DEFAULT_TILE_STEP): Int = ((contentWidth - TILE_ROW_PADDING - tileWidth).coerceAtLeast(0) / tileStep + 1).coerceAtLeast(1)

    /**
     * 計算牌面需要的列數。
     *
     * @param count 牌面數量。
     * @param tileWidth 單張牌寬度。
     * @param tileStep 牌面左界之間的距離。
     * @return 牌面列數。
     */
    fun tileRowCount(count: Int, tileWidth: Int = DEFAULT_TILE_WIDTH, tileStep: Int = DEFAULT_TILE_STEP): Int = if (count <= 0) 0 else (count + tilesPerRow(tileWidth, tileStep) - 1) / tilesPerRow(tileWidth, tileStep)

    /**
     * 取得底部按鈕列中一格的範圍：上一頁、範圍文字、下一頁與返回。
     *
     * @param index 由左至右的順序，從 0 開始。
     * @return 這一格的範圍。
     */
    fun footerSlot(index: Int): HistoryScreenLayout.Bounds = frame.footerSlot(index)

    /**
     * 保留捲軸滑塊內的抓取位置。
     *
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
        /**
         * 依畫面大小建立幾何，固定底列不與內容重疊。
         *
         * @param width 畫面寬度。
         * @param height 畫面高度。
         * @param contentTop 捲動內容上界，也就是固定區的下界。
         * @return 對應尺寸的版面。
         */
        fun measure(width: Int, height: Int, contentTop: Int): HistoryRoundEventsLayout {
            val frame = HistoryPageFrame(windowWidth = width, windowHeight = height)
            return HistoryRoundEventsLayout(frame = frame, contentTop = contentTop.coerceAtMost(frame.footerTop))
        }

        /** 牌面列左右內距總和。 */
        private const val TILE_ROW_PADDING = 24

        /** 預設牌面寬度。 */
        private const val DEFAULT_TILE_WIDTH = 18

        /** 預設牌面起點間距。 */
        private const val DEFAULT_TILE_STEP = 20
    }
}
