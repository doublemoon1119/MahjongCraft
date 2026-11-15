package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證牌譜列表在不同尺寸仍保留互不重疊的固定區域。 */
class HistoryScreenLayoutTest {
    /** 卡片點擊只落在背景可見範圍，不包含間隙、工具列、底列或捲軸。 */
    @Test
    fun `card hit testing excludes gaps clipping and scrollbar`() {
        val layout = measure(width = 800, height = 240)
        val heights = listOf(80, 80, 80)
        val top = layout.contentTop
        val x = layout.contentLeft + 4.0
        assertEquals(0, layout.cardIndexAt(x, top + 1.0, 0.0, heights))
        assertEquals(null, layout.cardIndexAt(x, top + 78.0, 0.0, heights))
        assertEquals(1, layout.cardIndexAt(x, top + 81.0, 0.0, heights))
        assertEquals(null, layout.cardIndexAt(layout.scrollbarBounds.x + 1.0, top + 1.0, 0.0, heights))
        assertEquals(null, layout.cardIndexAt(x, layout.contentTop - 1.0, 0.0, heights))
        assertEquals(null, layout.cardIndexAt(x, layout.contentBottom.toDouble(), 0.0, heights))
        assertEquals(1, layout.cardIndexAt(x, top + 1.0, 80.0, heights))
        assertEquals(null, layout.cardIndexAt(x, top + 1.0, 0.0, emptyList()))
    }

    /** 到達底端後連續向下捲動不得產生暫時越界的偏移。 */
    @Test
    fun `repeated scrolling beyond bottom keeps the same offset`() {
        val layout = measure(width = 800, height = 480)
        val contentHeight = 1000
        val bottom = layout.maximumScroll(contentHeight).toDouble()
        var offset = bottom
        repeat(100) {
            offset = layout.clampScroll(offset + 18.0, contentHeight)
            assertEquals(bottom, offset)
        }
        assertEquals(bottom - 18.0, layout.clampScroll(offset - 18.0, contentHeight))
    }

    /** 頂端、內容縮短與空清單均在繪製前校正至合法位置。 */
    @Test
    fun `scroll offsets clamp at top and after content shrinks`() {
        val layout = measure(width = 800, height = 480)
        assertEquals(0.0, layout.clampScroll(-18.0, 1000))
        assertEquals(0.0, layout.clampScroll(500.0, 10))
        assertEquals(0.0, layout.clampScroll(500.0, 0))
    }

    /** 底部四格橫跨內容寬度、互不重疊，關閉固定在最右一格。 */
    @Test
    fun `footer spans the content with close in the last slot`() {
        listOf(200, 320, 450, 800).forEach { width ->
            val layout = measure(width = width, height = 240)
            val slots = List(HistoryScreenLayout.FOOTER_SLOTS) { layout.footerSlot(it) }
            slots.zipWithNext().forEach { (left, right) -> assertTrue(left.right < right.x) }
            assertEquals(layout.contentLeft, slots.first().x)
            assertEquals(layout.contentRight, slots.last().right)
            assertTrue(slots.last().bottom <= 240)
        }
    }

    /** 內容寬度最多 456 並水平置中。 */
    @Test
    fun `content width is capped and centred`() {
        val layout = measure(width = 960, height = 540)
        assertEquals(456, layout.contentWidth)
        assertEquals((960 - 456) / 2, layout.contentLeft)
        assertEquals(layout.contentLeft, layout.cardBounds(top = 60, height = 42).x)
        assertEquals(layout.contentWidth, layout.cardBounds(top = 60, height = 42).width)
    }

    /** 寬畫面應具有正高度的內容區。 */
    @Test
    fun `wide viewport leaves a content region`() {
        val layout = measure(width = 800, height = 480)
        assertTrue(layout.hasContent)
        assertTrue(layout.contentTop < layout.contentBottom)
        assertTrue(layout.contentBottom <= layout.footerTop)
    }

    /** 極窄視窗不可產生負寬度或負座標。 */
    @Test
    fun `small viewport remains non negative`() {
        val layout = measure(width = 40, height = 30)
        assertTrue(layout.contentWidth >= 0)
        assertTrue(layout.contentTop >= 0)
        assertTrue(layout.contentBottom >= layout.contentTop)
    }

    /** 寬畫面工具列排成一列：標籤在按鈕左側，各控制項由左到右不重疊，重新查詢靠右。 */
    @Test
    fun `wide toolbar keeps every control on one row`() {
        val layout = measure(width = 960, height = 540)
        val ordered = listOf(layout.filters, layout.scope, layout.sort, layout.direction, layout.refresh)

        assertTrue(layout.singleRowToolbar)
        assertEquals(1, ordered.map { it.y }.distinct().size)
        ordered.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.x) }
        assertTrue(layout.filters.right < layout.showLabelX)
        assertEquals(layout.showLabelX + SHOW_LABEL + HistoryScreenLayout.LABEL_GAP, layout.scope.x)
        assertEquals(layout.sortLabelX + SORT_LABEL + HistoryScreenLayout.LABEL_GAP, layout.sort.x)
        assertEquals(layout.contentRight, layout.refresh.right)
        assertTrue(layout.toolbarBottom < layout.contentTop)
    }

    /** 窄畫面工具列改排兩列：篩選與重新查詢在上，範圍與排序在下，全部留在內容寬度內。 */
    @Test
    fun `narrow toolbar wraps into two rows inside the content`() {
        listOf(200, 320, 427).forEach { width ->
            val layout = measure(width = width, height = 240)
            val controls = listOf(layout.filters, layout.refresh, layout.scope, layout.sort, layout.direction)

            assertFalse(layout.singleRowToolbar)
            assertEquals(layout.filters.y, layout.refresh.y)
            assertEquals(layout.scope.y, layout.sort.y)
            assertTrue(layout.filters.bottom < layout.scope.y)
            assertTrue(layout.scope.right <= layout.sortLabelX)
            assertTrue(layout.sort.right <= layout.direction.x)
            controls.forEach { bounds ->
                assertTrue(bounds.x >= layout.contentLeft)
                assertTrue(bounds.right <= layout.contentRight)
                assertTrue(bounds.bottom <= layout.contentTop)
            }
        }
    }

    /** 捲軸畫在內容右側的邊距內，與內容區同高。 */
    @Test
    fun `scrollbar sits in the right margin`() {
        listOf(200, 320, 800).forEach { width ->
            val layout = measure(width = width, height = 480)
            val bounds = layout.scrollbarBounds
            assertTrue(bounds.x > layout.contentRight)
            assertTrue(bounds.right <= width)
            assertEquals(layout.contentTop, bounds.y)
            assertEquals(layout.contentBottom - layout.contentTop, bounds.height)
            assertTrue(layout.isOverScrollbar(bounds.x + 1.0, bounds.y + 1.0))
        }
    }

    /** 卡片背景在下方保留間距，負高度不產生負尺寸。 */
    @Test
    fun `cards keep a gap below and clamp invalid heights`() {
        val layout = measure(width = 800, height = 480)
        assertEquals(42 - HistoryScreenLayout.CARD_GAP, layout.cardBounds(top = 60, height = 42).height)
        assertEquals(0, layout.cardBounds(top = 60, height = -1).height)
    }

    /** 捲軸以內容像素數計算，且空內容與短軌道仍保持合法滑塊。 */
    @Test
    fun `pixel scrollbar has bounded index and minimum thumb`() {
        val layout = measure(width = 800, height = 480)
        val scrollbar = layout.scrollbar(1000, 500.75)
        assertEquals(1000, scrollbar.itemCount)
        assertEquals(layout.contentBottom - layout.contentTop, scrollbar.visibleItemCount)
        assertEquals(500, scrollbar.scrollIndex)
        assertTrue(scrollbar.minimumThumbHeight <= scrollbar.trackHeight)
        assertEquals(0, layout.scrollbar(0, 500.0).maximumScroll)

        val short = measure(width = 800, height = 80).scrollbar(100, 0.0)
        assertTrue(short.minimumThumbHeight <= short.trackHeight)
        assertTrue(short.thumbHeight <= short.trackHeight)
    }

    /** 拖曳抓取偏移與軌道兩端應映射至對應像素捲動位置。 */
    @Test
    fun `scrollbar drag preserves grab offset and maps track edges`() {
        val scrollbar = measure(width = 800, height = 480).scrollbar(1000, 0.0)
        val grabOffset = 12.0
        assertEquals(grabOffset, scrollbar.grabOffset(scrollbar.thumbTop + grabOffset))
        assertEquals(0, scrollbar.scrollIndexFor(scrollbar.trackTop.toDouble(), 0.0))
        assertEquals(scrollbar.maximumScroll, scrollbar.scrollIndexFor(scrollbar.trackBottom.toDouble(), 0.0))
    }

    /**
     * 以固定的標籤寬度建立版面。
     *
     * @param width 畫面寬度。
     * @param height 畫面高度。
     * @return 版面。
     */
    private fun measure(width: Int, height: Int): HistoryScreenLayout = HistoryScreenLayout.measure(
        width = width,
        height = height,
        showLabelWidth = SHOW_LABEL,
        sortLabelWidth = SORT_LABEL,
    )

    private companion object {
        /** 「顯示」標籤的寬度。 */
        const val SHOW_LABEL = 24

        /** 「排序」標籤的寬度。 */
        const val SORT_LABEL = 20
    }
}
