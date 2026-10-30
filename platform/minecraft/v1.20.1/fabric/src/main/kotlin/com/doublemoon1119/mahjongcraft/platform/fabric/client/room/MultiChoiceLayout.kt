package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollbarLayout

/**
 * 置中多選清單的位置；名稱、勾選框與捲軸共用同一個窄版內容區。
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
    /** 內容寬度不隨寬螢幕持續增加，窄視窗則保留左右邊界。 */
    val panelWidth: Int = minOf(260, (windowWidth - 32).coerceAtLeast(1))

    /** 置中內容區左界。 */
    val left: Int = (windowWidth - panelWidth) / 2

    /** 置中內容區右界。 */
    val right: Int = left + panelWidth

    /** 清單第一列頂端。 */
    val rowsTop: Int = 50

    /** 選項列的高度，含上下間距。 */
    val rowHeight: Int = 24

    /** 視窗內可完整顯示的列數，底部留給操作按鈕。 */
    val visibleCount: Int = ((windowHeight - 44 - rowsTop) / rowHeight).coerceAtLeast(1)

    /** 清單可捲動的最大索引。 */
    val maximumScroll: Int = (optionCount - visibleCount).coerceAtLeast(0)

    /** 名稱左界。 */
    val labelX: Int = left + 8

    /** 勾選框點擊範圍的左界，右側保留捲軸空間。 */
    val checkboxX: Int = right - 38

    /** 勾選框點擊範圍的寬度與高度；內側的視覺方框另行繪製。 */
    val checkboxSize: Int = 20

    /** 名稱與勾選框間保留間隔後的可用文字寬度。 */
    val labelWidth: Int = (checkboxX - labelX - 12).coerceAtLeast(1)

    /** 整列可點擊範圍的右界，不包含捲軸。 */
    val rowRight: Int = checkboxX + checkboxSize

    /** 捲軸左界。 */
    val scrollbarX: Int = right - 6

    /** 清單與操作按鈕共用的捲軸幾何。 */
    val scrollbar: ScrollbarLayout = ScrollbarLayout(
        trackTop = rowsTop,
        trackBottom = rowsTop + visibleCount * rowHeight,
        itemCount = optionCount,
        visibleItemCount = visibleCount,
        scrollIndex = scrollIndex,
        minimumThumbHeight = 8,
    )

    /** 底部按鈕頂端。 */
    val footerY: Int = windowHeight - 28

    /** 兩個操作按鈕的等寬寬度。 */
    val buttonWidth: Int = minOf(100, ((panelWidth - 8) / 2).coerceAtLeast(1))

    /** 套用按鈕左界。 */
    val applyX: Int = windowWidth / 2 - buttonWidth - 4

    /** 取消按鈕左界。 */
    val cancelX: Int = windowWidth / 2 + 4

    /**
     * 找出游標所指的可見選項列。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 選項在完整清單中的索引；游標不在選項列時為 null。
     */
    fun optionAt(mouseX: Double, mouseY: Double): Int? {
        if (mouseX < labelX || mouseX >= rowRight || mouseY < rowsTop) return null
        val row = ((mouseY - rowsTop) / rowHeight).toInt()
        if (row !in 0 until visibleCount) return null
        return (scrollIndex + row).takeIf { it in 0 until optionCount }
    }

    /** 判斷游標是否指向可捲動清單的捲軸。 */
    fun isOverScrollbar(mouseX: Double, mouseY: Double): Boolean = maximumScroll > 0 &&
        mouseX >= scrollbarX &&
        mouseX < right &&
        mouseY >= scrollbar.trackTop &&
        mouseY < scrollbar.trackBottom
}
