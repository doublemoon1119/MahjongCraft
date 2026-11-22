package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar

/**
 * 牌譜列表畫面的版面：標題下方的工具列、可捲動的對局卡片區與底部按鈕列。
 *
 * 內容寬度、底部四格與捲軸位置沿用 [HistoryPageFrame]。工具列由左到右為
 * 篩選、「顯示」標籤與紀錄範圍、「排序」標籤與排序欄位、排序方向，重新查詢靠右；內容寬度放不下時改排兩列：
 * 第一列為篩選與重新查詢，第二列為紀錄範圍與排序。
 *
 * 純資料，不依賴任何 Minecraft 型別；文字寬度由呼叫端量好傳入。
 *
 * @property windowWidth 目前視窗寬度。
 * @property windowHeight 目前視窗高度。
 * @property showLabelWidth 「顯示」標籤的文字寬度。
 * @property sortLabelWidth 「排序」標籤的文字寬度。
 */
internal data class HistoryScreenLayout(
    val windowWidth: Int,
    val windowHeight: Int,
    val showLabelWidth: Int,
    val sortLabelWidth: Int,
) {
    /** 內容寬度、底部四格與捲軸位置。 */
    val frame: HistoryPageFrame = HistoryPageFrame(windowWidth = windowWidth, windowHeight = windowHeight)

    /** 內容寬度。 */
    val contentWidth: Int = frame.contentWidth

    /** 內容左界。 */
    val contentLeft: Int = frame.contentLeft

    /** 內容右界。 */
    val contentRight: Int = frame.contentRight

    /** 工具列是否排成一列。 */
    val singleRowToolbar: Boolean = FILTERS_WIDTH + GROUP_GAP + showLabelWidth + LABEL_GAP + SCOPE_WIDTH + GROUP_GAP + sortLabelWidth + LABEL_GAP +
        SORT_WIDTH + CONTROL_GAP + DIRECTION_SIZE + GROUP_GAP + REFRESH_WIDTH <= contentWidth

    /** 篩選按鈕。 */
    val filters: Bounds

    /** 重新查詢按鈕；查詢失敗時的重試按鈕也放在這裡。 */
    val refresh: Bounds

    /** 「顯示」標籤的左界。 */
    val showLabelX: Int

    /** 紀錄範圍按鈕。 */
    val scope: Bounds

    /** 「排序」標籤的左界。 */
    val sortLabelX: Int

    /** 排序欄位選擇器。 */
    val sort: Bounds

    /** 排序方向按鈕。 */
    val direction: Bounds

    /** 工具列下界。 */
    val toolbarBottom: Int

    init {
        if (singleRowToolbar) {
            filters = Bounds(contentLeft, TOOLBAR_TOP, FILTERS_WIDTH, BUTTON_HEIGHT)
            showLabelX = filters.right + GROUP_GAP
            scope = Bounds(showLabelX + showLabelWidth + LABEL_GAP, TOOLBAR_TOP, SCOPE_WIDTH, BUTTON_HEIGHT)
            sortLabelX = scope.right + GROUP_GAP
            sort = Bounds(sortLabelX + sortLabelWidth + LABEL_GAP, TOOLBAR_TOP, SORT_WIDTH, BUTTON_HEIGHT)
            direction = Bounds(sort.right + CONTROL_GAP, TOOLBAR_TOP, DIRECTION_SIZE, BUTTON_HEIGHT)
            refresh = Bounds(contentRight - REFRESH_WIDTH, TOOLBAR_TOP, REFRESH_WIDTH, BUTTON_HEIGHT)
            toolbarBottom = TOOLBAR_TOP + BUTTON_HEIGHT
        } else {
            val columnWidth = ((contentWidth - GROUP_GAP) / 2).coerceAtLeast(1)
            val rightColumn = contentRight - columnWidth
            val secondRow = TOOLBAR_TOP + BUTTON_HEIGHT + ROW_GAP
            filters = Bounds(contentLeft, TOOLBAR_TOP, columnWidth, BUTTON_HEIGHT)
            refresh = Bounds(rightColumn, TOOLBAR_TOP, columnWidth, BUTTON_HEIGHT)
            showLabelX = contentLeft
            val scopeLeft = showLabelX + showLabelWidth + LABEL_GAP
            scope = Bounds(scopeLeft, secondRow, (contentLeft + columnWidth - scopeLeft).coerceAtLeast(1), BUTTON_HEIGHT)
            sortLabelX = rightColumn
            val sortLeft = sortLabelX + sortLabelWidth + LABEL_GAP
            direction = Bounds(contentRight - DIRECTION_SIZE, secondRow, DIRECTION_SIZE, BUTTON_HEIGHT)
            sort = Bounds(sortLeft, secondRow, (direction.x - CONTROL_GAP - sortLeft).coerceAtLeast(1), BUTTON_HEIGHT)
            toolbarBottom = secondRow + BUTTON_HEIGHT
        }
    }

    /** 工具列標籤文字的 Y 座標，與同一列的按鈕垂直置中。 */
    val labelY: Int = scope.y + (BUTTON_HEIGHT - FormScreenLayout.VISIBLE_TEXT_HEIGHT) / 2

    /** 底部按鈕列上界。 */
    val footerTop: Int = frame.footerTop

    /** 工具列下方存檔狀態文字的 Y 座標。 */
    val statusY: Int = (toolbarBottom + STATUS_GAP).coerceAtMost(footerTop)

    /** 捲動內容上界。 */
    val contentTop: Int = (toolbarBottom + STATUS_HEIGHT).coerceAtMost(footerTop)

    /** 捲動內容下界。 */
    val contentBottom: Int = (footerTop - HistoryPageFrame.FOOTER_GAP).coerceAtLeast(contentTop)

    /** 內容區是否有正高度。 */
    val hasContent: Boolean get() = contentBottom > contentTop

    /**
     * 取得底部按鈕列中一格的範圍：上一頁、筆數範圍、下一頁與關閉；筆數範圍佔一格但不是按鈕。
     *
     * @param index 由左至右的順序，從 0 開始。
     * @return 這一格的範圍。
     */
    fun footerSlot(index: Int): Bounds = frame.footerSlot(index)

    /**
     * 將捲動偏移限制於內容可見範圍，供輸入與繪製前共用。
     *
     * @param offset 要套用的像素偏移。
     * @param contentHeight 清單內容總高度。
     * @return 不超過頂端及底端的偏移。
     */
    fun clampScroll(offset: Double, contentHeight: Int): Double = offset.coerceIn(0.0, maximumScroll(contentHeight).toDouble())

    /**
     * 計算清單內容超出可視區的高度。
     *
     * @param contentHeight 清單內容總高度。
     * @return 允許的最大像素偏移。
     */
    fun maximumScroll(contentHeight: Int): Int = (contentHeight - (contentBottom - contentTop).coerceAtLeast(1)).coerceAtLeast(0)

    /** 捲軸軌道的範圍，畫在內容右側的邊距內，不佔用卡片寬度。 */
    val scrollbarBounds: Bounds
        get() = frame.scrollbarBounds(top = contentTop, bottom = contentBottom)

    /**
     * 判斷游標是否落在捲軸的命中範圍內；命中範圍比繪製範圍稍寬。
     *
     * @param mouseX 游標的水平座標。
     * @param mouseY 游標的垂直座標。
     * @return 位於命中範圍內時為 true。
     */
    fun isOverScrollbar(mouseX: Double, mouseY: Double): Boolean = frame.isOverScrollbar(mouseX = mouseX, mouseY = mouseY, top = contentTop, bottom = contentBottom)

    /**
     * 計算單張對局卡片的背景範圍：橫跨內容寬度，下方保留卡片間距。
     *
     * @param top 卡片上界。
     * @param height 卡片含間距的完整高度。
     * @return 卡片背景的範圍。
     */
    fun cardBounds(top: Int, height: Int): Bounds = Bounds(contentLeft, top, contentWidth, (height - CARD_GAP).coerceAtLeast(0))

    /**
     * 判斷游標指向的可見卡片，排除卡片間隙、捲軸與內容區以外的範圍。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param scrollOffset 清單的像素捲動偏移。
     * @param heights 每張卡片含間距的完整高度。
     * @return 指向卡片的索引；沒有可見卡片時為 null。
     */
    fun cardIndexAt(mouseX: Double, mouseY: Double, scrollOffset: Double, heights: List<Int>): Int? {
        if (mouseY < contentTop || mouseY >= contentBottom) return null
        val total = heights.sum()
        var top = contentTop - clampScroll(scrollOffset, total).toInt()
        heights.forEachIndexed { index, height ->
            val bounds = cardBounds(top, height)
            if (mouseX >= bounds.x && mouseX < bounds.right && mouseY >= bounds.y && mouseY < bounds.bottom) return index
            top += height
        }
        return null
    }

    /**
     * 依內容像素高度建立列表捲軸幾何。
     *
     * @param contentHeight 清單內容總高度。
     * @param scrollOffset 目前像素捲動偏移。
     * @return 捲軸軌道、滑塊與目前位置的幾何資料。
     */
    fun scrollbar(contentHeight: Int, scrollOffset: Double): ScrollbarLayout {
        val viewportHeight = (contentBottom - contentTop).coerceAtLeast(1)
        return ScrollbarLayout(
            trackTop = contentTop,
            trackBottom = contentBottom,
            itemCount = contentHeight,
            visibleItemCount = viewportHeight,
            scrollIndex = clampScroll(scrollOffset, contentHeight).toInt(),
            minimumThumbHeight = VerticalScrollbar.MINIMUM_THUMB_HEIGHT.coerceAtMost(viewportHeight),
        )
    }

    /**
     * 可重用的矩形控制項界線。
     *
     * @property x 左側座標。
     * @property y 上側座標。
     * @property width 寬度。
     * @property height 高度。
     */
    data class Bounds(val x: Int, val y: Int, val width: Int, val height: Int) {
        /** 右界。 */
        val right: Int get() = x + width

        /** 下界。 */
        val bottom: Int get() = y + height
    }

    internal companion object {
        /** 工具列第一列的上界。 */
        const val TOOLBAR_TOP: Int = FormScreenLayout.TITLE_BOTTOM

        /** 按鈕高度。 */
        const val BUTTON_HEIGHT: Int = FormScreenLayout.BUTTON_HEIGHT

        /** 單列工具列中篩選按鈕的寬度。 */
        const val FILTERS_WIDTH: Int = 75

        /** 單列工具列中紀錄範圍按鈕的寬度。 */
        const val SCOPE_WIDTH: Int = 90

        /** 單列工具列中排序欄位選擇器的寬度。 */
        const val SORT_WIDTH: Int = 90

        /** 排序方向按鈕的邊長。 */
        const val DIRECTION_SIZE: Int = 20

        /** 單列工具列中重新查詢按鈕的寬度。 */
        const val REFRESH_WIDTH: Int = 60

        /** 工具列各組之間的水平間距。 */
        const val GROUP_GAP: Int = 12

        /** 標籤與按鈕之間的水平間距。 */
        const val LABEL_GAP: Int = 6

        /** 排序欄位與排序方向之間的水平間距。 */
        const val CONTROL_GAP: Int = 4

        /** 兩列工具列之間的垂直間距。 */
        const val ROW_GAP: Int = 4

        /** 工具列與存檔狀態文字之間的垂直間距。 */
        const val STATUS_GAP: Int = 3

        /** 工具列與內容區之間保留給存檔狀態文字的高度。 */
        const val STATUS_HEIGHT: Int = 14

        /** 卡片之間的垂直間距。 */
        const val CARD_GAP: Int = 4

        /**
         * 依畫面尺寸建立版面。
         *
         * @param width 畫面寬度。
         * @param height 畫面高度。
         * @param showLabelWidth 「顯示」標籤的文字寬度。
         * @param sortLabelWidth 「排序」標籤的文字寬度。
         * @return 對應尺寸的版面幾何資料。
         */
        fun measure(width: Int, height: Int, showLabelWidth: Int = 0, sortLabelWidth: Int = 0): HistoryScreenLayout = HistoryScreenLayout(
            windowWidth = width,
            windowHeight = height,
            showLabelWidth = showLabelWidth,
            sortLabelWidth = sortLabelWidth,
        )
    }
}
