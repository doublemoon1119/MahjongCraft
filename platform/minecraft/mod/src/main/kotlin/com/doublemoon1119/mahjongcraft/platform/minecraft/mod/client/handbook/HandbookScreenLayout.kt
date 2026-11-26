package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.handbook

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ButtonRowLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar

/**
 * 麻將手冊快捷畫面的版面：標題下方一列一個入口，入口放不下時捲動，底部按鈕列上方是開啟方式的提示。
 *
 * 寬版的入口寬度最多 [ENTRY_MAX_WIDTH] 並水平置中。窄版的入口佔滿內容寬度，且不顯示開啟方式的提示。
 * 內容寬度、邊距與底部按鈕列沿用 [FormScreenLayout]。
 *
 * @property windowWidth 視窗寬度。
 * @property windowHeight 視窗高度。
 * @property entryCount 入口數量。
 * @property scrollIndex 第一個可見入口的索引。
 */
internal data class HandbookScreenLayout(
    val windowWidth: Int,
    val windowHeight: Int,
    val entryCount: Int,
    val scrollIndex: Int,
) {
    /** 內容寬度、入口區與底部按鈕列的共用版面。 */
    private val frame = FormScreenLayout(
        windowWidth = windowWidth,
        windowHeight = windowHeight,
        hasPageSwitch = false,
        hasRuleRow = false,
        hasTabs = false,
    )

    /** 入口寬度。 */
    val entryWidth: Int = if (frame.isCompact) frame.contentWidth else ENTRY_MAX_WIDTH.coerceAtMost(frame.contentWidth)

    /** 入口左界，水平置中。 */
    val entryLeft: Int = (windowWidth - entryWidth) / 2

    /** 第一個入口的上界。 */
    val entriesTop: Int = frame.fieldsTop

    /** 視窗內可完整顯示的入口數。 */
    val visibleCount: Int = frame.completeRowCount(rowHeight = ENTRY_STEP, controlHeight = ENTRY_HEIGHT)

    /** 入口清單可捲動的最大索引。 */
    val maximumScroll: Int = if (visibleCount == 0) 0 else (entryCount - visibleCount).coerceAtLeast(0)

    /** 捲軸左界，在入口右側。 */
    val scrollbarX: Int = entryLeft + entryWidth + FormScreenLayout.SCROLLBAR_GAP

    /** 入口清單的捲軸幾何。 */
    val scrollbar: ScrollbarLayout = ScrollbarLayout(
        trackTop = entriesTop,
        trackBottom = entriesTop + ((visibleCount - 1) * ENTRY_STEP + ENTRY_HEIGHT).coerceAtLeast(0),
        itemCount = if (visibleCount > 0) entryCount else 0,
        visibleItemCount = visibleCount,
        scrollIndex = scrollIndex,
        minimumThumbHeight = VerticalScrollbar.MINIMUM_THUMB_HEIGHT,
    )

    /** 是否顯示開啟方式的提示。窄版不顯示。 */
    val showsOpenHint: Boolean = !frame.isCompact

    /** 開啟方式提示的 Y 座標，在底部按鈕列上方。 */
    val openHintY: Int = frame.statusY

    /** 開啟方式提示可用的寬度。 */
    val contentWidth: Int = frame.contentWidth

    /** 底部按鈕列上界。 */
    val footerY: Int = frame.footerY

    /** 底部四格按鈕列。只使用右下角的關閉。 */
    val footer: ButtonRowLayout = frame.footer(buttonCount = FOOTER_BUTTONS)

    /**
     * 取得第幾個可見入口的上界。
     *
     * @param visibleIndex 入口在可見範圍內的順序，從 0 開始。
     * @return 入口上界。
     */
    fun entryTop(visibleIndex: Int): Int = entriesTop + visibleIndex * ENTRY_STEP

    /**
     * 判斷游標是否位於可捲動的入口區或其捲軸。
     *
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 清單可捲動且游標在入口區或捲軸上時為 true。
     */
    fun isOverScrollableArea(mouseX: Double, mouseY: Double): Boolean = maximumScroll > 0 &&
        mouseX >= entryLeft &&
        mouseX < scrollbarX + VerticalScrollbar.WIDTH + FormScreenLayout.SCROLLBAR_HIT_PADDING &&
        mouseY >= scrollbar.trackTop &&
        mouseY < scrollbar.trackBottom

    /**
     * 判斷游標是否指向捲軸。命中範圍比繪製範圍稍寬。
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
        /** 寬版入口的最大寬度。 */
        const val ENTRY_MAX_WIDTH: Int = 300

        /** 入口高度，容納圖示、名稱與一行說明。 */
        const val ENTRY_HEIGHT: Int = 32

        /** 相鄰兩個入口上界之間的距離。 */
        const val ENTRY_STEP: Int = ENTRY_HEIGHT + 6

        /** 底部按鈕數量，與其他畫面相同。 */
        const val FOOTER_BUTTONS: Int = 4

        /** 關閉按鈕的順序，固定在右下角。 */
        const val CLOSE: Int = 3
    }
}
