package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證分類頁籤列的寬度縮放、置中與左右捲動。 */
class TabStripLayoutTest {
    /** 放得下時每個頁籤寬 90、間距 2，整排置中且不能捲動。 */
    @Test
    fun `tabs keep the preferred width and stay centred when they fit`() {
        val strip = TabStripLayout(left = 12, width = 456, top = 77, tabCount = 3)

        assertEquals(90, strip.tabWidth)
        assertEquals(274, strip.totalWidth)
        assertEquals(0, strip.maximumScroll)
        assertEquals(12 + (456 - 274) / 2, strip.tabX(0))
        assertEquals(strip.tabX(0) + 92, strip.tabX(1))
        assertEquals(12 + 456, strip.right)
    }

    /** 放不下時平均縮窄，最窄 60；縮窄後整排仍在頁籤列範圍內。 */
    @Test
    fun `tabs shrink evenly down to the minimum width`() {
        val strip = TabStripLayout(left = 18, width = 284, top = 77, tabCount = 4)

        assertEquals(69, strip.tabWidth)
        assertEquals(0, strip.maximumScroll)
        assertTrue(strip.tabX(0) >= strip.left)
        assertTrue(strip.tabX(3) + strip.tabWidth <= strip.right)
    }

    /** 縮到最窄仍放不下時整排靠左，可以向左捲動到最後一個頁籤對齊右界。 */
    @Test
    fun `tabs scroll once the minimum width no longer fits`() {
        val strip = TabStripLayout(left = 18, width = 284, top = 77, tabCount = 6)

        assertEquals(60, strip.tabWidth)
        assertEquals(370, strip.totalWidth)
        assertEquals(86, strip.maximumScroll)
        assertEquals(18, strip.tabX(0))
        val scrolled = strip.copy(scroll = 500)
        assertEquals(86, scrolled.effectiveScroll)
        assertEquals(scrolled.right, scrolled.tabX(5) + scrolled.tabWidth)
        assertEquals(0, strip.copy(scroll = -40).effectiveScroll)
    }

    /** 選中的頁籤高 21 並向上突出，其他頁籤高 18；兩者底邊都貼著底線。 */
    @Test
    fun `selected tab is taller and both kinds end at the line`() {
        val strip = TabStripLayout(left = 12, width = 456, top = 77, tabCount = 3)

        assertEquals(21, strip.tabHeight(selected = true))
        assertEquals(18, strip.tabHeight(selected = false))
        assertEquals(77, strip.tabY(selected = true))
        assertEquals(80, strip.tabY(selected = false))
        assertEquals(98, strip.lineY)
        assertEquals(strip.lineY, strip.tabY(selected = true) + strip.tabHeight(selected = true))
        assertEquals(strip.lineY, strip.tabY(selected = false) + strip.tabHeight(selected = false))
    }

    /** 選中的頁籤被捲出範圍時，捲動到剛好完整顯示；已完整顯示時不動。 */
    @Test
    fun `reveals a tab that is scrolled out of view`() {
        val strip = TabStripLayout(left = 18, width = 284, top = 77, tabCount = 6)

        assertEquals(0, strip.scrollRevealing(0))
        assertEquals(0, strip.scrollRevealing(3))
        assertEquals(86, strip.scrollRevealing(5))
        assertEquals(24, strip.scrollRevealing(4))
        val scrolled = strip.copy(scroll = 86)
        assertEquals(86, scrolled.scrollRevealing(4))
        assertEquals(62, scrolled.scrollRevealing(1))
        assertEquals(86, scrolled.scrollRevealing(9))
    }

    /** 滾輪一次捲動一個頁籤的距離，並停在兩端。 */
    @Test
    fun `wheel scrolls one tab at a time within bounds`() {
        val strip = TabStripLayout(left = 18, width = 284, top = 77, tabCount = 6)

        assertEquals(62, strip.scrollByWheel(-1.0))
        assertEquals(86, strip.copy(scroll = 62).scrollByWheel(-1.0))
        assertEquals(0, strip.scrollByWheel(1.0))
        assertEquals(24, strip.copy(scroll = 86).scrollByWheel(1.0))
        assertEquals(40, strip.copy(scroll = 40).scrollByWheel(0.0))
    }

    /** 命中範圍只涵蓋頁籤列本身，不含底線以下。 */
    @Test
    fun `contains only the strip area`() {
        val strip = TabStripLayout(left = 12, width = 456, top = 77, tabCount = 3)

        assertTrue(strip.contains(12.0, 77.0))
        assertTrue(strip.contains(467.0, 97.0))
        assertFalse(strip.contains(468.0, 80.0))
        assertFalse(strip.contains(100.0, 98.0))
        assertFalse(strip.contains(100.0, 76.0))
    }
}
