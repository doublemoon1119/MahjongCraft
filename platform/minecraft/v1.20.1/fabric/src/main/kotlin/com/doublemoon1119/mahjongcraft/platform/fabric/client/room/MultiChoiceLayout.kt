package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ButtonRowLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.RowStripe
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.VerticalScrollbar

/**
 * 多選畫面的版面，與設定頁的表單相同：標題下方一行已選數量，接著一列一個選項，名稱靠左、勾選方塊靠右，
 * 底部四顆按鈕。內容寬度與邊距沿用 [FormScreenLayout]；視窗窄時選項仍維持一列，不改成標籤在上。
 *
 * @property windowWidth 視窗寬度。
 * @property windowHeight 視窗高度。
 * @property optionCount 完整選項數量。
 * @property scrollIndex 第一個可見選項的索引。
 */
internal data class MultiChoiceLayout(
    val windowWidth: Int,
    val windowHeight: Int,
    val optionCount: Int,
    val scrollIndex: Int,
) {
    /** 內容寬度、標題下方說明區、欄位區與底部按鈕列的共用版面。 */
    private val frame = FormScreenLayout(
        windowWidth = windowWidth,
        windowHeight = windowHeight,
        hasPageSwitch = false,
        hasRuleRow = false,
        hasTabs = false,
        headerHeight = COUNT_HEIGHT,
    )

    /** 內容左界。 */
    val contentLeft: Int = frame.contentLeft

    /** 內容右界。 */
    val contentRight: Int = frame.contentRight

    /** 內容寬度。 */
    val contentWidth: Int = frame.contentWidth

    /** 標題下方已選數量文字的 Y 座標。 */
    val countY: Int = frame.headerTop

    /** 清單第一列頂端。 */
    val rowsTop: Int = frame.fieldsTop

    /** 相鄰兩列頂端之間的距離。 */
    val rowHeight: Int = FormScreenLayout.REGULAR_FIELD_ROW_HEIGHT

    /** 視窗內可完整顯示的列數。 */
    val visibleCount: Int = frame.completeRowCount(rowHeight = rowHeight, controlHeight = FormScreenLayout.BUTTON_HEIGHT)

    /** 清單可捲動的最大索引。 */
    val maximumScroll: Int = if (visibleCount == 0) 0 else (optionCount - visibleCount).coerceAtLeast(0)

    /** 勾選方塊的邊長。 */
    val checkboxSize: Int = FormScreenLayout.BUTTON_HEIGHT

    /** 勾選方塊左界，靠內容右緣。 */
    val checkboxX: Int = contentRight - checkboxSize

    /** 名稱可用的寬度。 */
    val labelWidth: Int = (checkboxX - FormScreenLayout.LABEL_GAP - contentLeft).coerceAtLeast(1)

    /** 捲軸左界，在內容右側的邊距內。 */
    val scrollbarX: Int = frame.scrollbarLeft

    /** 清單的捲軸幾何。 */
    val scrollbar: ScrollbarLayout = ScrollbarLayout(
        trackTop = rowsTop,
        trackBottom = rowsTop + ((visibleCount - 1) * rowHeight + FormScreenLayout.BUTTON_HEIGHT).coerceAtLeast(0),
        itemCount = if (visibleCount > 0) optionCount else 0,
        visibleItemCount = visibleCount,
        scrollIndex = scrollIndex,
        minimumThumbHeight = VerticalScrollbar.MINIMUM_THUMB_HEIGHT,
    )

    /** 底部按鈕列上界。 */
    val footerY: Int = frame.footerY

    /** 底部四顆按鈕：全選、清除、取消、完成。 */
    val footer: ButtonRowLayout = frame.footer(buttonCount = FOOTER_BUTTONS)

    /**
     * 取得第幾個可見列的頂端。
     *
     * @param visibleIndex 列在可見範圍內的順序，從 0 開始。
     * @return 列頂端。
     */
    fun rowTop(visibleIndex: Int): Int = rowsTop + visibleIndex * rowHeight

    /**
     * 取得可見列的底色範圍；上下延伸到相鄰列的中間，也是整列可點擊的範圍。
     *
     * @param visibleIndex 列在可見範圍內的順序，從 0 開始。
     * @return 底色與點擊範圍。
     */
    fun rowArea(visibleIndex: Int): RowStripe {
        val top = rowTop(visibleIndex) - (rowHeight - FormScreenLayout.BUTTON_HEIGHT) / 2
        return RowStripe(left = contentLeft - FormScreenLayout.STRIPE_INSET, top = top, right = contentRight + FormScreenLayout.STRIPE_INSET, bottom = minOf(top + rowHeight, frame.fieldsBottom))
    }

    /**
     * 找出游標所指的可見選項列。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 選項在完整清單中的索引；游標不在選項列時為 null。
     */
    fun optionAt(mouseX: Double, mouseY: Double): Int? {
        val first = rowArea(0)
        if (mouseX < first.left || mouseX >= first.right || mouseY < first.top || mouseY >= frame.fieldsBottom) return null
        val row = ((mouseY - first.top) / rowHeight).toInt()
        if (row !in 0 until visibleCount) return null
        return (scrollIndex + row).takeIf { it in 0 until optionCount }
    }

    /**
     * 判斷游標是否指向可捲動清單的捲軸；命中範圍比繪製範圍稍寬。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 清單可捲動且游標在捲軸上時為 true。
     */
    fun isOverScrollbar(mouseX: Double, mouseY: Double): Boolean = maximumScroll > 0 &&
        mouseX >= scrollbarX - FormScreenLayout.SCROLLBAR_HIT_PADDING &&
        mouseX < scrollbarX + VerticalScrollbar.WIDTH + FormScreenLayout.SCROLLBAR_HIT_PADDING &&
        mouseY >= scrollbar.trackTop &&
        mouseY < scrollbar.trackBottom

    internal companion object {
        /** 標題下方已選數量文字所佔的高度。 */
        const val COUNT_HEIGHT: Int = 12

        /** 底部按鈕數量。 */
        const val FOOTER_BUTTONS: Int = 4

        /** 全選按鈕的順序。 */
        const val SELECT_ALL: Int = 0

        /** 清除按鈕的順序。 */
        const val CLEAR: Int = 1

        /** 取消按鈕的順序。 */
        const val CANCEL: Int = 2

        /** 完成按鈕的順序。 */
        const val DONE: Int = 3
    }
}
