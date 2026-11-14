package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 驗證設定頁底部四顆按鈕等寬排成一列並橫跨整個內容寬度。 */
class SettingsFooterLayoutTest {
    /** 寬度可以整除時四顆按鈕完全等寬，最後一顆的右界對齊內容右界。 */
    @Test
    fun `buttons share the width equally and span the content`() {
        val layout = SettingsFooterLayout(left = 12, width = 456)

        assertEquals(listOf(110, 110, 109, 109), (0 until 4).map(layout::buttonWidth))
        assertEquals(listOf(12, 128, 244, 359), (0 until 4).map(layout::buttonX))
        assertEquals(12 + 456, layout.buttonX(SettingsFooterLayout.DONE) + layout.buttonWidth(SettingsFooterLayout.DONE))
    }

    /** 寬度無法整除時按鈕寬度最多相差一像素，彼此不重疊，仍橫跨整個寬度。 */
    @Test
    fun `leftover pixels go to the first buttons`() {
        (200..460).forEach { width ->
            val layout = SettingsFooterLayout(left = 7, width = width)
            val widths = (0 until 4).map(layout::buttonWidth)

            assertTrue(widths.max() - widths.min() <= 1, "Button widths differ by more than a pixel at width $width")
            assertEquals(width, widths.sum() + layout.gap * 3)
            (1 until 4).forEach { index ->
                assertEquals(layout.buttonX(index - 1) + layout.buttonWidth(index - 1) + layout.gap, layout.buttonX(index))
            }
            assertEquals(7 + width, layout.buttonX(3) + layout.buttonWidth(3))
        }
    }

    /** 寬度容不下間距或按鈕順序超出範圍時拒絕。 */
    @Test
    fun `rejects impossible widths and unknown buttons`() {
        assertFailsWith<IllegalArgumentException> { SettingsFooterLayout(left = 0, width = 18) }
        assertFailsWith<IllegalArgumentException> { SettingsFooterLayout(left = 0, width = 200).buttonWidth(4) }
    }
}
