package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證單局事件與局面畫面的內容區、捲軸與底部四格。 */
class HistoryRoundEventsLayoutTest {
    /** 窄畫面仍保留可用的內容區，內容區在底部按鈕列上方。 */
    @Test
    fun `layout keeps footer below content`() {
        val layout = HistoryRoundEventsLayout.measure(320, 240, contentTop = 48)

        assertTrue(layout.contentTop < layout.contentBottom)
        assertEquals(layout.contentBottom + HistoryPageFrame.FOOTER_GAP, layout.footerTop)
    }

    /** 底部四格在各種寬度都排成一列、互不重疊，返回固定在最右一格。 */
    @Test
    fun `footer slots stay on one row at supported widths`() {
        listOf(200, 320, 480, 800).forEach { width ->
            val layout = HistoryRoundEventsLayout.measure(width, 360, contentTop = 48)
            val slots = List(HistoryPageFrame.FOOTER_SLOTS) { layout.footerSlot(it) }

            assertTrue(slots.all { it.width > 0 && it.y == layout.footerTop && it.bottom <= 360 })
            slots.zipWithNext().forEach { (left, right) -> assertTrue(left.right < right.x, "Footer slots overlap at width $width") }
            assertEquals(layout.right, slots[HistoryPageFrame.LAST_SLOT].right)
        }
    }

    /** 內容寬度最多 456 並水平置中。 */
    @Test
    fun `content is capped and centred`() {
        val layout = HistoryRoundEventsLayout.measure(960, 540, contentTop = 48)

        assertEquals(456, layout.contentWidth)
        assertEquals((960 - 456) / 2, layout.left)
    }

    /** 捲動位置不會超出內容上下界。 */
    @Test
    fun `scroll is clamped to viewport`() {
        val layout = HistoryRoundEventsLayout.measure(800, 600, contentTop = 48)

        assertEquals(0.0, layout.clampScroll(-10.0, 100))
        assertEquals(0.0, layout.clampScroll(10_000.0, 100))
        assertEquals(layout.maximumScroll(1_000).toDouble(), layout.clampScroll(10_000.0, 1_000))
    }

    /** 長牌列會依內容寬度換行，窄畫面也至少容納一張牌。 */
    @Test
    fun `tile rows wrap using the content width`() {
        val narrow = HistoryRoundEventsLayout.measure(200, 360, contentTop = 48)
        val wide = HistoryRoundEventsLayout.measure(800, 360, contentTop = 48)

        assertTrue(narrow.tilesPerRow() >= 1)
        assertTrue(narrow.tileRowCount(14) > 1)
        assertTrue(wide.tilesPerRow() > narrow.tilesPerRow())
        assertEquals(0, wide.tileRowCount(0))
        listOf(narrow, wide).forEach { layout ->
            val lastTileRight = layout.left + 16 + (layout.tilesPerRow() - 1) * 20 + 18
            assertTrue(lastTileRight <= layout.right - 8, "Tile row exceeds card padding")
        }
    }

    /** 卡片命中區不包含捲軸與內容區以外的游標；捲軸在內容右側的邊距內。 */
    @Test
    fun `card hit testing is clipped to content`() {
        val layout = HistoryRoundEventsLayout.measure(800, 360, contentTop = 48)
        val scrollbar = layout.scrollbarBounds()

        assertTrue(layout.containsCard(layout.left + 1.0, layout.contentTop + 2.0, layout.contentTop, 40))
        assertTrue(!layout.containsCard(scrollbar.x + 1.0, layout.contentTop + 2.0, layout.contentTop, 40))
        assertTrue(!layout.containsCard(layout.left + 1.0, layout.footerTop + 1.0, layout.contentTop, 40))
        assertTrue(scrollbar.x > layout.right)
        assertTrue(layout.isOverScrollbar(scrollbar.x + 1.0, scrollbar.y + 1.0))
    }

    /** 捲軸拖曳抓取位置在同一位置時不會自行跳動。 */
    @Test
    fun `scrollbar grab offset is stable at current position`() {
        val layout = HistoryRoundEventsLayout.measure(800, 600, contentTop = 48)
        val offset = 120.0
        val bar = layout.scrollbar(2_000, offset)
        val mouseY = bar.thumbTop + bar.thumbHeight / 2.0

        val grab = layout.grabOffset(2_000, offset, mouseY)
        val restored = layout.scrollbar(2_000, offset).scrollIndexFor(mouseY, grab)
        assertEquals(offset.toInt(), restored)
    }
}
