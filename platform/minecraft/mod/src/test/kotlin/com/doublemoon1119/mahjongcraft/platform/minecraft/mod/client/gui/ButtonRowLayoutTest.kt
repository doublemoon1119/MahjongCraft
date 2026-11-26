package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 驗證按鈕等寬排成一列並橫跨整個寬度。 */
class ButtonRowLayoutTest {
    /** 四顆按鈕橫跨內容寬度，最後一顆的右界對齊整列的右界。 */
    @Test
    fun `buttons share the width equally and span the row`() {
        val layout = ButtonRowLayout(left = 12, width = 456, buttonCount = 4)

        assertEquals(listOf(110, 110, 109, 109), (0 until 4).map(layout::buttonWidth))
        assertEquals(listOf(12, 128, 244, 359), (0 until 4).map(layout::buttonX))
        assertEquals(12 + 456, layout.buttonX(3) + layout.buttonWidth(3))
    }

    /** 寬度無法整除時按鈕寬度最多相差一像素，彼此不重疊，仍橫跨整個寬度。任意顆數都成立。 */
    @Test
    fun `leftover pixels go to the first buttons for any button count`() {
        (1..6).forEach { count ->
            (200..460).forEach { width ->
                val layout = ButtonRowLayout(left = 7, width = width, buttonCount = count)
                val widths = (0 until count).map(layout::buttonWidth)

                assertTrue(widths.max() - widths.min() <= 1, "Button widths differ by more than a pixel at width $width with $count buttons")
                assertEquals(width, widths.sum() + layout.gap * (count - 1))
                (1 until count).forEach { index ->
                    assertEquals(layout.buttonX(index - 1) + layout.buttonWidth(index - 1) + layout.gap, layout.buttonX(index))
                }
                assertEquals(7 + width, layout.buttonX(count - 1) + layout.buttonWidth(count - 1))
            }
        }
    }

    /** 單顆按鈕佔滿整列。 */
    @Test
    fun `single button fills the row`() {
        val layout = ButtonRowLayout(left = 20, width = 150, buttonCount = 1)

        assertEquals(20, layout.buttonX(0))
        assertEquals(150, layout.buttonWidth(0))
    }

    /** 沒有按鈕、寬度容不下間距或按鈕順序超出範圍時拒絕。 */
    @Test
    fun `rejects impossible rows and unknown buttons`() {
        assertFailsWith<IllegalArgumentException> { ButtonRowLayout(left = 0, width = 200, buttonCount = 0) }
        assertFailsWith<IllegalArgumentException> { ButtonRowLayout(left = 0, width = 18, buttonCount = 4) }
        assertFailsWith<IllegalArgumentException> { ButtonRowLayout(left = 0, width = 200, buttonCount = 4).buttonWidth(4) }
    }

    /** 最小寬度剛好讓每顆按鈕寬 1 像素。 */
    @Test
    fun `minimum width fits one pixel per button`() {
        (1..6).forEach { count ->
            val layout = ButtonRowLayout(left = 0, width = ButtonRowLayout.minimumWidth(count), buttonCount = count)

            assertEquals(List(count) { 1 }, (0 until count).map(layout::buttonWidth))
        }
    }
}
