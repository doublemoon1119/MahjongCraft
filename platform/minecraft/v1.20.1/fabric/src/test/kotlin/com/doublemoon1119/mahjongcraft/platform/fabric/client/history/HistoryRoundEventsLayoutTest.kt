package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證單局事件畫面固定底列與內容捲動幾何。 */
class HistoryRoundEventsLayoutTest {
    /** 窄畫面仍保留可用的內容區與固定 footer。 */
    @Test
    fun `layout keeps footer below content`() {
        val layout = HistoryRoundEventsLayout.measure(320, 240, headerHeight = 48)

        assertTrue(layout.contentTop < layout.contentBottom)
        assertEquals(layout.contentBottom + 4, layout.footerTop)
    }

    /** 四個 footer 槽位在寬畫面不重疊，窄畫面改為兩列。 */
    @Test
    fun `footer slots remain separate at supported widths`() {
        listOf(200, 320, 480, 800).forEach { width ->
            val layout = HistoryRoundEventsLayout.measure(width, 360)
            val slots = layout.footerBounds(width)
            assertEquals(4, slots.size)
            assertTrue(slots.all { it.width > 0 && it.y + it.height <= 360 })
            assertEquals(24, slots[2].height)
            slots.forEachIndexed { index, slot ->
                slots.drop(index + 1).forEach { other ->
                    val overlaps = slot.x < other.x + other.width &&
                        other.x < slot.x + slot.width &&
                        slot.y < other.y + other.height &&
                        other.y < slot.y + slot.height
                    assertTrue(!overlaps, "Footer slots overlap at width $width")
                }
            }
        }
    }

    /** 捲動位置不會超出內容上下界。 */
    @Test
    fun `scroll is clamped to viewport`() {
        val layout = HistoryRoundEventsLayout.measure(800, 600)

        assertEquals(0.0, layout.clampScroll(-10.0, 100))
        assertEquals(0.0, layout.clampScroll(10_000.0, 100))
        assertEquals(layout.maximumScroll(1_000).toDouble(), layout.clampScroll(10_000.0, 1_000))
    }

    /** 長牌列會依保留後的內容寬度換行，窄畫面也至少容納一張牌。 */
    @Test
    fun `tile rows wrap using reserved content width`() {
        val narrow = HistoryRoundEventsLayout.measure(200, 360)
        val wide = HistoryRoundEventsLayout.measure(800, 360)

        assertTrue(narrow.tilesPerRow() >= 1)
        assertTrue(narrow.tileRowCount(14) > 1)
        assertTrue(wide.tilesPerRow() > narrow.tilesPerRow())
        assertEquals(0, wide.tileRowCount(0))
        listOf(narrow, wide).forEach { layout ->
            val lastTileRight = layout.left + 16 + (layout.tilesPerRow() - 1) * 20 + 18
            assertTrue(lastTileRight <= layout.cardRight - 8, "Tile row exceeds card padding")
        }
    }

    /** 卡片命中區不會把捲軸或內容區外的游標算進來。 */
    @Test
    fun `card hit testing is clipped to content and scrollbar`() {
        val layout = HistoryRoundEventsLayout.measure(800, 360)

        assertTrue(layout.containsCard(layout.left + 1.0, layout.contentTop + 2.0, layout.contentTop, 40))
        assertTrue(!layout.containsCard(layout.right - 2.0, layout.contentTop + 2.0, layout.contentTop, 40))
        assertTrue(!layout.containsCard(layout.left + 1.0, layout.footerTop + 1.0, layout.contentTop, 40))
    }

    /** 捲軸拖曳抓取位置在同一位置時不會自行跳動。 */
    @Test
    fun `scrollbar grab offset is stable at current position`() {
        val layout = HistoryRoundEventsLayout.measure(800, 600)
        val offset = 120.0
        val bar = layout.scrollbar(2_000, offset)
        val mouseY = bar.thumbTop + bar.thumbHeight / 2.0

        val grab = layout.grabOffset(2_000, offset, mouseY)
        val restored = layout.scrollbar(2_000, offset).scrollIndexFor(mouseY, grab)
        assertEquals(offset.toInt(), restored)
    }
}
