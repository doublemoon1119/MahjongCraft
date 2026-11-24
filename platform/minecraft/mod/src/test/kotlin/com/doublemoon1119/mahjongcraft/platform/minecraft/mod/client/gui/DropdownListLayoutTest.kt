package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui

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
        val layout = DropdownListLayout(
            anchorLeft = 318,
            anchorTop = 48,
            anchorBottom = 68,
            width = 150,
            optionCount = 3,
            footnoteHeight = 0,
            windowHeight = 270,
            footnoteLineHeight = FONT_HEIGHT,
        )

        assertEquals(318, layout.left)
        assertEquals(468, layout.right)
        assertEquals(68, layout.top)
        assertEquals(3, layout.visibleCount)
        assertEquals(69, layout.optionTop(0))
        assertEquals(89, layout.optionTop(1))
        assertEquals(68 + 1 + 60 + 1, layout.bottom)
        assertEquals(0, layout.maximumScroll)
        assertEquals(0, layout.footnoteBlockHeight)
        assertEquals(0, layout.optionIndexAt(layout.scrollbarLeft.toDouble(), layout.optionsTop.toDouble()))
    }

    /** 底部說明加上分隔線與上下內距，接在選項下方。 */
    @Test
    fun `footnote adds a separated block under the options`() {
        val layout = DropdownListLayout(
            anchorLeft = 318,
            anchorTop = 48,
            anchorBottom = 68,
            width = 150,
            optionCount = 2,
            footnoteHeight = 27,
            windowHeight = 270,
            footnoteLineHeight = FONT_HEIGHT,
        )

        assertEquals(1 + 4 + 27 + 4, layout.footnoteBlockHeight)
        assertEquals(layout.optionsBottom + 1 + 4, layout.footnoteTop)
        assertEquals(layout.optionsBottom + 36 + 1, layout.bottom)
    }

    /** 選項超出視窗底部時只顯示放得下的列數，其餘在清單內捲動。 */
    @Test
    fun `list scrolls when options do not fit above the window bottom`() {
        val layout = DropdownListLayout(
            anchorLeft = 10,
            anchorTop = 20,
            anchorBottom = 40,
            width = 150,
            optionCount = 12,
            footnoteHeight = 0,
            windowHeight = 130,
            footnoteLineHeight = FONT_HEIGHT,
        )

        assertFalse(layout.opensUpward)
        assertEquals(4, layout.visibleCount)
        assertEquals(8, layout.maximumScroll)
        assertTrue(layout.bottom <= 130 - 4)
        assertEquals(8, layout.copy(scrollIndex = 30).effectiveScroll)
        assertEquals(0, layout.copy(scrollIndex = -2).effectiveScroll)
        assertEquals(8, layout.scrollbar.maximumScroll)
    }

    /** 空間再小也至少顯示一列。 */
    @Test
    fun `at least one option stays visible`() {
        val layout = DropdownListLayout(
            anchorLeft = 10,
            anchorTop = 10,
            anchorBottom = 30,
            width = 150,
            optionCount = 5,
            footnoteHeight = 18,
            windowHeight = 60,
            footnoteLineHeight = FONT_HEIGHT,
        )

        assertEquals(1, layout.visibleCount)
        assertEquals(4, layout.maximumScroll)
        assertEquals(0, layout.visibleFootnoteLines)
        assertTrue(layout.bottom <= 60 - DropdownListLayout.WINDOW_MARGIN)
    }

    /** 說明再長也先保留可操作的選項列，且清單上下緣都在視窗內。 */
    @Test
    fun `long footnotes use only the space left after two options`() {
        for (anchorTop in listOf(16, 84)) {
            val layout = DropdownListLayout(
                anchorLeft = 10,
                anchorTop = anchorTop,
                anchorBottom = anchorTop + 20,
                width = 150,
                optionCount = 5,
                footnoteHeight = 90,
                windowHeight = 120,
                footnoteLineHeight = FONT_HEIGHT,
            )

            assertEquals(anchorTop == 84, layout.opensUpward)
            assertEquals(2, layout.visibleCount)
            assertEquals(3, layout.visibleFootnoteLines)
            assertTrue(layout.top >= DropdownListLayout.WINDOW_MARGIN)
            assertTrue(layout.bottom <= 120 - DropdownListLayout.WINDOW_MARGIN)
            assertEquals(2, layout.copy(scrollIndex = 2).effectiveScroll)
        }
    }

    /** 按鈕上下都不足一列時，清單改在視窗內覆蓋按鈕。 */
    @Test
    fun `list can overlap the button when neither side fits an option`() {
        val layout = DropdownListLayout(
            anchorLeft = 10,
            anchorTop = 16,
            anchorBottom = 36,
            width = 150,
            optionCount = 5,
            footnoteHeight = 90,
            windowHeight = 52,
            footnoteLineHeight = FONT_HEIGHT,
        )

        assertEquals(2, layout.visibleCount)
        assertEquals(0, layout.visibleFootnoteLines)
        assertTrue(layout.top >= DropdownListLayout.WINDOW_MARGIN)
        assertEquals(52 - DropdownListLayout.WINDOW_MARGIN, layout.bottom)
    }

    /** 游標位置換算成完整清單中的選項索引，計入捲動位置；外框與底部說明不算選項。 */
    @Test
    fun `pointer maps to the option under it`() {
        val layout = DropdownListLayout(
            anchorLeft = 10,
            anchorTop = 20,
            anchorBottom = 40,
            width = 150,
            optionCount = 12,
            footnoteHeight = 0,
            windowHeight = 130,
            footnoteLineHeight = FONT_HEIGHT,
            scrollIndex = 3,
        )

        assertEquals(3, layout.optionIndexAt(20.0, 41.0))
        assertEquals(4, layout.optionIndexAt(20.0, 61.0))
        val scrollbarX = layout.scrollbarLeft.toDouble()
        assertEquals(6, layout.optionIndexAt(scrollbarX - 0.1, 120.9))
        assertNull(layout.optionIndexAt(scrollbarX, 120.9))
        assertNull(layout.optionIndexAt(158.0, 120.9))
        assertTrue(layout.contains(scrollbarX, 120.9))
        assertNull(layout.optionIndexAt(10.0, 50.0))
        assertNull(layout.optionIndexAt(20.0, 40.5))
        assertNull(layout.optionIndexAt(20.0, 121.0))
        assertTrue(layout.contains(10.0, 40.0))
        assertFalse(layout.contains(9.0, 50.0))
        assertFalse(layout.contains(20.0, layout.bottom.toDouble()))
    }

    /** 下方放不下完整清單、上方空間較大時，清單改在按鈕正上方展開，下界貼齊按鈕上界。 */
    @Test
    fun `list opens upward when the space below is too small`() {
        val layout = DropdownListLayout(
            anchorLeft = 10,
            anchorTop = 242,
            anchorBottom = 262,
            width = 150,
            optionCount = 2,
            footnoteHeight = 18,
            windowHeight = 270,
            footnoteLineHeight = FONT_HEIGHT,
        )

        assertTrue(layout.opensUpward)
        assertEquals(2, layout.visibleCount)
        assertEquals(242, layout.bottom)
        assertEquals(242 - (1 + 40 + 27 + 1), layout.top)
        assertEquals(layout.top + 1, layout.optionsTop)
        assertEquals(0, layout.optionIndexAt(20.0, layout.optionsTop + 1.0))
        assertTrue(layout.top >= 4)
    }

    /** 下方放得下時一律往下展開，即使上方空間更大。 */
    @Test
    fun `list stays below while it fits`() {
        val layout = DropdownListLayout(
            anchorLeft = 10,
            anchorTop = 180,
            anchorBottom = 200,
            width = 150,
            optionCount = 2,
            footnoteHeight = 0,
            windowHeight = 270,
            footnoteLineHeight = FONT_HEIGHT,
        )

        assertFalse(layout.opensUpward)
        assertEquals(200, layout.top)
    }

    /** 兩側都放不下時選空間較大的一側，並在清單內捲動。 */
    @Test
    fun `list picks the roomier side and scrolls when neither fits`() {
        val layout = DropdownListLayout(
            anchorLeft = 10,
            anchorTop = 160,
            anchorBottom = 180,
            width = 150,
            optionCount = 20,
            footnoteHeight = 0,
            windowHeight = 270,
            footnoteLineHeight = FONT_HEIGHT,
        )

        assertTrue(layout.opensUpward)
        assertEquals((160 - 4 - 2) / 20, layout.visibleCount)
        assertEquals(160, layout.bottom)
        assertTrue(layout.top >= 4)
        assertTrue(layout.maximumScroll > 0)
    }

    private companion object {
        /** Minecraft 預設字型的字高，說明區以此為單行高度。 */
        const val FONT_HEIGHT = 9
    }
}
