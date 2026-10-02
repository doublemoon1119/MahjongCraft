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
}
