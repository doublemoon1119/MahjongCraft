package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證歷史摘要內容與固定底列的幾何不重疊。 */
class HistorySummaryLayoutTest {
    /** 同一局的多個換行列可點擊，但不接受裁切區或捲軸的點擊。 */
    @Test
    fun `round links preserve identity across wrapped rows and scrolling`() {
        val layout = HistorySummaryLayout.measure(320, 180)
        val links = listOf(
            HistorySummaryLayout.RoundLink(18, 50, 7),
            HistorySummaryLayout.RoundLink(18, 61, 7),
            HistorySummaryLayout.RoundLink(18, 80, 9),
        )
        assertEquals(7, layout.roundAt(20.0, 31.0, 20.0, links))
        assertEquals(7, layout.roundAt(20.0, 45.0, 20.0, links))
        assertEquals(9, layout.roundAt(20.0, 61.0, 20.0, links))
        assertEquals(null, layout.roundAt(20.0, 54.0, 20.0, links))
        assertEquals(null, layout.roundAt(310.0, 31.0, 20.0, links))
        assertEquals(null, layout.roundAt(20.0, 23.0, 30.0, links))
    }

    /** 可點擊列只接受自身可見部分，不接受裁切區、捲軸或列間留白。 */
    @Test
    fun `clickable rows exclude clipped areas scrollbar and spacing`() {
        val layout = HistorySummaryLayout.measure(320, 180)
        assertTrue(layout.containsContentRow(20.0, 26.0, 18, 20, 11))
        assertTrue(!layout.containsContentRow(20.0, 23.0, 18, 20, 11))
        assertTrue(!layout.containsContentRow(20.0, 31.0, 18, 20, 11))
        assertTrue(!layout.containsContentRow(310.0, 26.0, 18, 20, 11))
        assertTrue(!layout.containsContentRow(17.0, 26.0, 18, 20, 11))
        assertTrue(!layout.containsContentRow(20.0, layout.contentBottom.toDouble(), 18, layout.contentBottom - 2, 11))
    }

    /** 不同畫面尺寸的捲軸均位於內容裁切內且不侵入底列。 */
    @Test
    fun `summary scrollbar stays inside content across gui sizes`() {
        for ((width, height) in listOf(320 to 180, 480 to 270, 800 to 480)) {
            val layout = HistorySummaryLayout.measure(width, height)
            val bounds = layout.scrollbarBounds()
            assertTrue(bounds.x >= 8)
            assertTrue(bounds.x + bounds.width <= width - 8)
            assertTrue(bounds.y + bounds.height <= layout.footerTop)
            val bar = layout.scrollbar(1000, 100.0)
            val mouseY = bar.thumbTop + bar.thumbHeight / 2.0
            assertEquals(100, bar.scrollIndexFor(mouseY, layout.grabOffset(1000, 100.0, mouseY)))
            assertEquals(0, bar.scrollIndexFor(-100.0, 0.0))
            assertEquals(bar.maximumScroll, bar.scrollIndexFor(2000.0, 0.0))
        }
    }

    /** 摘要版面保留內容區與固定底列。 */
    @Test
    fun `summary layout reserves footer`() {
        val layout = HistorySummaryLayout.measure(800, 480)
        assertTrue(layout.contentTop < layout.contentBottom)
        assertTrue(layout.contentBottom <= layout.footerTop)
        assertEquals(6, layout.scrollbarBounds().width)
    }

    /** 摘要捲動偏移在內容縮短後仍會被限制。 */
    @Test
    fun `summary scroll clamps after content shrinks`() {
        val layout = HistorySummaryLayout.measure(800, 480)
        assertEquals(0.0, layout.clampScroll(-1.0, 100))
        assertEquals(0.0, layout.clampScroll(1000.0, 10))
        assertTrue(layout.scrollbar(1000, 100.0).thumbHeight <= layout.viewportHeight)
    }

    /** 只有返回按鈕時置中；顯示重試時兩顆按鈕並排置中、返回在左，且都在畫面內。 */
    @Test
    fun `footer centers the back button alone and pairs it with retry`() {
        listOf(320, 427, 961).forEach { width ->
            val layout = HistorySummaryLayout.measure(width, 240)
            val (alone, none) = layout.footerButtons(width, retryVisible = false)
            val (back, retry) = layout.footerButtons(width, retryVisible = true)

            assertEquals(null, none)
            assertTrue(abs(alone.x - (width - alone.x - alone.width)) <= 1, "back button must be centered")
            val pairRight = checkNotNull(retry).x + retry.width
            assertTrue(abs(back.x - (width - pairRight)) <= 1, "pair must be centered")
            assertEquals(back.x + back.width + 4, retry.x)
            assertTrue(back.x >= 0 && pairRight <= width)
            assertEquals(layout.footerTop, alone.y)
            assertEquals(layout.footerTop, retry.y)
        }
    }
}
