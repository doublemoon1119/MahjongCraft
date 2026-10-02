package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證歷史畫面在不同尺寸仍保留互不重疊的固定區域。 */
class HistoryScreenLayoutTest {
    /** 到達底端後連續向下捲動不得產生暫時越界的偏移。 */
    @Test
    fun `repeated scrolling beyond bottom keeps the same offset`() {
        val layout = HistoryScreenLayout.measure(800, 480)
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
        val layout = HistoryScreenLayout.measure(800, 480)
        assertEquals(0.0, layout.clampScroll(-18.0, 1000))
        assertEquals(0.0, layout.clampScroll(500.0, 10))
        assertEquals(0.0, layout.clampScroll(500.0, 0))
    }

    /** 分頁文字使用專用欄位，不與左右按鈕共享中心座標。 */
    @Test
    fun `page label has its own footer column`() {
        listOf(200, 320, 450, 800).forEach { width ->
            val layout = HistoryScreenLayout.measure(width, 240)
            val footer = layout.footerBounds(width, layout.footerTop)
            footer.zipWithNext().forEach { (left, right) -> assertTrue(left.x + left.width < right.x) }
        }
    }

    /** 寬畫面應具有正高度的內容區。 */
    @Test
    fun `wide viewport leaves a content region`() {
        val layout = HistoryScreenLayout.measure(800, 480)
        assertTrue(layout.hasContent)
        assertTrue(layout.contentTop < layout.contentBottom)
        assertTrue(layout.contentBottom <= layout.footerTop)
    }

    /** 極窄視窗不可產生負寬度或負座標。 */
    @Test
    fun `small viewport remains non negative`() {
        val layout = HistoryScreenLayout.measure(40, 30)
        assertTrue(layout.contentWidth >= 0)
        assertTrue(layout.contentTop >= 0)
        assertTrue(layout.contentBottom >= layout.contentTop)
    }

    /** 工具列與底列在常見小視窗寬度內不會超出右界。 */
    @Test
    fun `toolbar and footer fit narrow widths`() {
        listOf(200, 320, 450, 800).forEach { width ->
            val layout = HistoryScreenLayout.measure(width, 240)
            layout.toolbarBounds(width).forEach { bound -> assertTrue(bound.x + bound.width <= width) }
            layout.toolbarBounds(width).forEach { bound -> assertTrue(bound.y + bound.height <= layout.contentTop - 14) }
            layout.footerBounds(width, layout.footerTop).forEach { bound -> assertTrue(bound.x + bound.width <= width) }
        }
    }

    /** 捲軸界線應完整落在內容裁切範圍內。 */
    @Test
    fun `scrollbar stays inside content scissor`() {
        listOf(8, 12, 20, 320, 800).forEach { width ->
            val layout = HistoryScreenLayout.measure(width, 480)
            val bounds = layout.scrollbarBounds(width)
            assertTrue(bounds.x >= 8)
            assertTrue(bounds.x + bounds.width <= (width - 8).coerceAtLeast(8))
            assertEquals(layout.contentTop, bounds.y)
            assertEquals((layout.contentBottom - layout.contentTop).coerceAtLeast(0), bounds.height)
        }
    }

    /** 卡片背景應避開捲軸，且不因窄寬或負高度產生負尺寸。 */
    @Test
    fun `cards do not overlap scrollbar and clamp invalid dimensions`() {
        val layout = HistoryScreenLayout.measure(800, 480)
        val withScrollbar = layout.cardBounds(800, 60, 42, scrollbarVisible = true)
        val scrollbar = layout.scrollbarBounds(800)
        assertTrue(withScrollbar.x + withScrollbar.width <= scrollbar.x)
        assertEquals(40, withScrollbar.height)

        val withoutScrollbar = layout.cardBounds(800, 60, 42, scrollbarVisible = false)
        assertEquals(790, withoutScrollbar.x + withoutScrollbar.width)
        assertEquals(0, layout.cardBounds(12, 60, -1, scrollbarVisible = true).width)
        assertEquals(0, layout.cardBounds(800, 60, -1, scrollbarVisible = false).height)
    }

    /** 捲軸以內容像素數計算，且空內容與短軌道仍保持合法 thumb。 */
    @Test
    fun `pixel scrollbar has bounded index and minimum thumb`() {
        val layout = HistoryScreenLayout.measure(800, 480)
        val scrollbar = layout.scrollbar(1000, 500.75)
        assertEquals(1000, scrollbar.itemCount)
        assertEquals(layout.contentBottom - layout.contentTop, scrollbar.visibleItemCount)
        assertEquals(500, scrollbar.scrollIndex)
        assertTrue(scrollbar.minimumThumbHeight <= scrollbar.trackHeight)
        assertEquals(0, layout.scrollbar(0, 500.0).maximumScroll)

        val short = HistoryScreenLayout(0, 10, 12, 20, 0).scrollbar(100, 0.0)
        assertTrue(short.minimumThumbHeight <= short.trackHeight)
        assertTrue(short.thumbHeight <= short.trackHeight)
    }

    /** 拖曳抓取偏移與軌道兩端應映射至對應像素捲動位置。 */
    @Test
    fun `scrollbar drag preserves grab offset and maps track edges`() {
        val scrollbar = HistoryScreenLayout.measure(800, 480).scrollbar(1000, 0.0)
        val grabOffset = 12.0
        assertEquals(grabOffset, scrollbar.grabOffset(scrollbar.thumbTop + grabOffset))
        assertEquals(0, scrollbar.scrollIndexFor(scrollbar.trackTop.toDouble(), 0.0))
        assertEquals(scrollbar.maximumScroll, scrollbar.scrollIndexFor(scrollbar.trackBottom.toDouble(), 0.0))
    }
}
