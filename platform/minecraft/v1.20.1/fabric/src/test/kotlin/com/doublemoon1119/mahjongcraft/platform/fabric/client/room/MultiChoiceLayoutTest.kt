package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證多選清單在不同視窗仍置中且緊湊，名稱、勾選框與捲軸不重疊。 */
class MultiChoiceLayoutTest {
    /** 內容區在寬螢幕不會隨視窗持續拉寬，窄視窗則保留左右空間。 */
    @Test
    fun `content stays compact and centered across window sizes`() {
        for (width in listOf(240, 320, 640, 1280)) {
            val layout = MultiChoiceLayout(width, 240, 8, 0)
            assertTrue(layout.panelWidth <= 260)
            assertTrue(abs(layout.left - (width - layout.right)) <= 1)
            assertTrue(layout.left >= 16)
            assertTrue(layout.labelX + layout.labelWidth < layout.checkboxX)
            assertTrue(layout.rowRight < layout.scrollbarX)
            assertTrue(layout.scrollbarX < layout.right)
            assertTrue(layout.applyX >= layout.left)
            assertTrue(layout.cancelX + layout.buttonWidth <= layout.right)
            assertTrue(layout.scrollbar.trackBottom < layout.footerY)
        }
    }

    /** 捲動後整列點擊對應同一個選項，捲軸及頁尾不會被當成選項。 */
    @Test
    fun `row hit testing uses the visible scroll offset and excludes scrollbar`() {
        val layout = MultiChoiceLayout(640, 240, 20, 4)
        val y = layout.rowsTop + 10.0
        assertEquals(4, layout.optionAt(layout.labelX.toDouble(), y))
        assertEquals(4, layout.optionAt(layout.checkboxX + 10.0, y))
        assertNull(layout.optionAt(layout.scrollbarX.toDouble(), y))
        assertTrue(layout.isOverScrollbar(layout.scrollbarX.toDouble(), y))
        assertNull(layout.optionAt(layout.labelX.toDouble(), layout.footerY.toDouble()))
    }

    /** 清單最後一頁的空白列不能切換不存在的選項。 */
    @Test
    fun `unused rows do not select an option`() {
        val layout = MultiChoiceLayout(640, 360, 2, 0)
        assertEquals(0, layout.maximumScroll)
        assertNull(layout.optionAt(layout.labelX.toDouble(), layout.rowsTop + 2.0 * layout.rowHeight))
    }
}
