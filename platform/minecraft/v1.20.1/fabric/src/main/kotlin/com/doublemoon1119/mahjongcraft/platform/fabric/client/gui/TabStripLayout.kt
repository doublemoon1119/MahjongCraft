package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

/**
 * 分類頁籤列的版面：一排頁籤與貫穿內容寬度的底線。
 *
 * 頁籤寬 [PREFERRED_TAB_WIDTH] 並整排置中；放不下時平均縮窄，最窄 [MINIMUM_TAB_WIDTH]；仍放不下時整排靠左
 * 並可左右捲動。選中的頁籤比其他頁籤高，向上突出並與底線相連。
 *
 * @property left 頁籤列左界。
 * @property width 頁籤列寬度。
 * @property top 頁籤列上界，也就是選中頁籤的上界。
 * @property tabCount 頁籤數量。
 * @property scroll 向左捲動的像素數；超出合法範圍的部分不採用。
 */
internal data class TabStripLayout(
    val left: Int,
    val width: Int,
    val top: Int,
    val tabCount: Int,
    val scroll: Int = 0,
) {
    /** 每個頁籤的寬度。 */
    val tabWidth: Int = if (tabCount <= 0) {
        PREFERRED_TAB_WIDTH
    } else {
        ((width - (tabCount - 1) * TAB_GAP) / tabCount).coerceIn(MINIMUM_TAB_WIDTH, PREFERRED_TAB_WIDTH)
    }

    /** 整排頁籤的總寬度。 */
    val totalWidth: Int = if (tabCount <= 0) 0 else tabCount * tabWidth + (tabCount - 1) * TAB_GAP

    /** 最大捲動量；整排頁籤放得下時為 0。 */
    val maximumScroll: Int = (totalWidth - width).coerceAtLeast(0)

    /** 實際採用的捲動量。 */
    val effectiveScroll: Int = scroll.coerceIn(0, maximumScroll)

    /** 頁籤列右界。 */
    val right: Int = left + width

    /** 底線的 Y 座標。 */
    val lineY: Int = top + SELECTED_HEIGHT

    /** 第一個頁籤的左界。 */
    private val firstTabX: Int = if (maximumScroll == 0) left + (width - totalWidth) / 2 else left - effectiveScroll

    /**
     * 取得頁籤的左界。
     *
     * @param index 頁籤順序，從 0 開始。
     * @return 套用捲動後的左界，可能落在頁籤列範圍之外。
     */
    fun tabX(index: Int): Int = firstTabX + index * (tabWidth + TAB_GAP)

    /**
     * 取得頁籤的上界。
     *
     * @param selected 是否為選中的頁籤。
     * @return 選中時與頁籤列同高，否則較低的上界。
     */
    fun tabY(selected: Boolean): Int = if (selected) top else top + SELECTED_HEIGHT - UNSELECTED_HEIGHT

    /**
     * 取得頁籤的高度。
     *
     * @param selected 是否為選中的頁籤。
     * @return 頁籤高度。
     */
    fun tabHeight(selected: Boolean): Int = if (selected) SELECTED_HEIGHT else UNSELECTED_HEIGHT

    /**
     * 計算讓指定頁籤完整顯示所需的捲動量；已完整顯示時維持目前捲動量。
     *
     * @param index 頁籤順序，從 0 開始。
     * @return 合法範圍內的捲動量。
     */
    fun scrollRevealing(index: Int): Int {
        if (maximumScroll == 0 || index !in 0 until tabCount) return effectiveScroll
        val tabLeft = index * (tabWidth + TAB_GAP)
        val tabRight = tabLeft + tabWidth
        return when {
            tabLeft < effectiveScroll -> tabLeft
            tabRight > effectiveScroll + width -> tabRight - width
            else -> effectiveScroll
        }.coerceIn(0, maximumScroll)
    }

    /**
     * 依滾輪方向捲動一個頁籤的距離。
     *
     * @param amount 滾輪位移；負值向右看後面的頁籤，正值向左。
     * @return 合法範圍內的捲動量。
     */
    fun scrollByWheel(amount: Double): Int {
        if (amount == 0.0) return effectiveScroll
        val step = tabWidth + TAB_GAP
        return (effectiveScroll + if (amount < 0) step else -step).coerceIn(0, maximumScroll)
    }

    /**
     * 判斷游標是否位於頁籤列範圍內。
     *
     * @param mouseX 游標的水平座標。
     * @param mouseY 游標的垂直座標。
     * @return 位於頁籤列內時為 true。
     */
    fun contains(mouseX: Double, mouseY: Double): Boolean = mouseX >= left && mouseX < right && mouseY >= top && mouseY < lineY

    internal companion object {
        /** 頁籤放得下時的寬度。 */
        const val PREFERRED_TAB_WIDTH: Int = 90

        /** 頁籤縮窄後的最小寬度。 */
        const val MINIMUM_TAB_WIDTH: Int = 60

        /** 相鄰頁籤的間距。 */
        const val TAB_GAP: Int = 2

        /** 選中頁籤的高度。 */
        const val SELECTED_HEIGHT: Int = 21

        /** 未選中頁籤的高度。 */
        const val UNSELECTED_HEIGHT: Int = 18
    }
}
