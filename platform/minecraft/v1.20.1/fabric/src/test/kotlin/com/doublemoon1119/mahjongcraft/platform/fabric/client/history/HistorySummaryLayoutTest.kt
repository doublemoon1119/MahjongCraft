package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證牌譜摘要的內容區、捲軸與底部四格不重疊。 */
class HistorySummaryLayoutTest {
    /** 可點擊的列只接受自身可見部分，不接受裁切區、內容左右以外或列下方。 */
    @Test
    fun `rows accept only their visible part inside the content`() {
        val layout = HistorySummaryLayout.measure(320, 180)
        val x = layout.left + 4.0
        val rowTop = layout.contentTop + 10

        assertTrue(layout.containsRow(x, rowTop + 1.0, rowTop, 18))
        assertFalse(layout.containsRow(x, rowTop - 1.0, rowTop, 18))
        assertFalse(layout.containsRow(x, rowTop + 18.0, rowTop, 18))
        assertFalse(layout.containsRow(layout.right.toDouble(), rowTop + 1.0, rowTop, 18))
        assertFalse(layout.containsRow(layout.left - 1.0, rowTop + 1.0, rowTop, 18))
        assertFalse(layout.containsRow(x, layout.contentBottom.toDouble(), layout.contentBottom - 2, 18))
    }

    /** 不同畫面尺寸的捲軸都在內容右側的邊距內，且拖曳位置能對應回捲動偏移。 */
    @Test
    fun `scrollbar sits in the right margin across gui sizes`() {
        for ((width, height) in listOf(320 to 180, 480 to 270, 800 to 480)) {
            val layout = HistorySummaryLayout.measure(width, height)
            val bounds = layout.scrollbarBounds()
            assertTrue(bounds.x > layout.right)
            assertTrue(bounds.right <= width)
            assertTrue(bounds.bottom <= layout.footerTop)
            assertTrue(layout.isOverScrollbar(bounds.x + 1.0, bounds.y + 1.0))
            val bar = layout.scrollbar(1000, 100.0)
            val mouseY = bar.thumbTop + bar.thumbHeight / 2.0
            assertEquals(100, bar.scrollIndexFor(mouseY, layout.grabOffset(1000, 100.0, mouseY)))
            assertEquals(0, bar.scrollIndexFor(-100.0, 0.0))
            assertEquals(bar.maximumScroll, bar.scrollIndexFor(2000.0, 0.0))
        }
    }

    /** 內容寬度最多 456 並水平置中，內容區在底部按鈕列上方。 */
    @Test
    fun `content is capped centred and above the footer`() {
        val layout = HistorySummaryLayout.measure(960, 540)
        assertEquals(456, layout.right - layout.left)
        assertEquals((960 - 456) / 2, layout.left)
        assertTrue(layout.contentTop < layout.contentBottom)
        assertTrue(layout.contentBottom <= layout.footerTop)
    }

    /** 捲動偏移在內容縮短後仍會被限制。 */
    @Test
    fun `scroll clamps after content shrinks`() {
        val layout = HistorySummaryLayout.measure(800, 480)
        assertEquals(0.0, layout.clampScroll(-1.0, 100))
        assertEquals(0.0, layout.clampScroll(1000.0, 10))
        assertTrue(layout.scrollbar(1000, 100.0).thumbHeight <= layout.viewportHeight)
    }

    /** 底部四格橫跨內容寬度，最右一格貼齊內容右界並留在畫面內。 */
    @Test
    fun `footer slots span the content with the last slot on the right`() {
        listOf(320, 427, 961).forEach { width ->
            val layout = HistorySummaryLayout.measure(width, 240)
            val slots = List(HistoryPageFrame.FOOTER_SLOTS) { layout.footerSlot(it) }

            slots.zipWithNext().forEach { (left, right) -> assertTrue(left.right < right.x) }
            assertEquals(layout.left, slots.first().x)
            assertEquals(layout.right, slots[HistoryPageFrame.LAST_SLOT].right)
            assertTrue(slots.all { it.y == layout.footerTop && it.bottom <= 240 })
        }
    }
}
