package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證選擇器展開清單的位置、可見列數、捲動與命中判定。 */
class DropdownListLayoutTest {
    /** 清單在按鈕正下方、與按鈕同寬，每個選項高 20，外框各佔 1。 */
    @Test
    fun `list opens under the button with the same width`() {
        val layout = DropdownListLayout(anchorLeft = 318, anchorBottom = 68, width = 150, optionCount = 3, footnoteHeight = 0, windowHeight = 270)

        assertEquals(318, layout.left)
        assertEquals(468, layout.right)
        assertEquals(68, layout.top)
        assertEquals(3, layout.visibleCount)
        assertEquals(69, layout.optionTop(0))
        assertEquals(89, layout.optionTop(1))
        assertEquals(68 + 1 + 60 + 1, layout.bottom)
        assertEquals(0, layout.maximumScroll)
        assertEquals(0, layout.footnoteBlockHeight)
    }

    /** 底部說明加上分隔線與上下內距，接在選項下方。 */
    @Test
    fun `footnote adds a separated block under the options`() {
        val layout = DropdownListLayout(anchorLeft = 318, anchorBottom = 68, width = 150, optionCount = 2, footnoteHeight = 27, windowHeight = 270)

        assertEquals(1 + 4 + 27 + 4, layout.footnoteBlockHeight)
        assertEquals(layout.optionsBottom + 1 + 4, layout.footnoteTop)
        assertEquals(layout.optionsBottom + 36 + 1, layout.bottom)
    }

    /** 選項超出視窗底部時只顯示放得下的列數，其餘在清單內捲動。 */
    @Test
    fun `list scrolls when options do not fit above the window bottom`() {
        val layout = DropdownListLayout(anchorLeft = 10, anchorBottom = 150, width = 150, optionCount = 12, footnoteHeight = 0, windowHeight = 240)

        assertEquals(4, layout.visibleCount)
        assertEquals(8, layout.maximumScroll)
        assertTrue(layout.bottom <= 240 - 4)
        assertEquals(8, layout.copy(scrollIndex = 30).effectiveScroll)
        assertEquals(0, layout.copy(scrollIndex = -2).effectiveScroll)
        assertEquals(8, layout.scrollbar.maximumScroll)
    }

    /** 空間再小也至少顯示一列。 */
    @Test
    fun `at least one option stays visible`() {
        val layout = DropdownListLayout(anchorLeft = 10, anchorBottom = 230, width = 150, optionCount = 5, footnoteHeight = 18, windowHeight = 240)

        assertEquals(1, layout.visibleCount)
        assertEquals(4, layout.maximumScroll)
    }

    /** 游標位置換算成完整清單中的選項索引，計入捲動位置；外框與底部說明不算選項。 */
    @Test
    fun `pointer maps to the option under it`() {
        val layout = DropdownListLayout(
            anchorLeft = 10,
            anchorBottom = 150,
            width = 150,
            optionCount = 12,
            footnoteHeight = 0,
            windowHeight = 240,
            scrollIndex = 3,
        )

        assertEquals(3, layout.optionIndexAt(20.0, 151.0))
        assertEquals(4, layout.optionIndexAt(20.0, 171.0))
        assertEquals(6, layout.optionIndexAt(158.0, 230.9))
        assertNull(layout.optionIndexAt(10.0, 160.0))
        assertNull(layout.optionIndexAt(20.0, 150.5))
        assertNull(layout.optionIndexAt(20.0, 231.0))
        assertTrue(layout.contains(10.0, 150.0))
        assertFalse(layout.contains(9.0, 160.0))
        assertFalse(layout.contains(20.0, layout.bottom.toDouble()))
    }
}
