package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證多選畫面與設定頁表單同寬，名稱、勾選方塊、捲軸與底部按鈕不重疊。 */
class MultiChoiceLayoutTest {
    /** 短視窗沒有完整列時，選項不可命中，也不顯示捲軸。 */
    @Test
    fun `requires the full height of a choice before exposing it`() {
        val frame = MultiChoiceLayout(windowWidth = 640, windowHeight = 400, optionCount = 10, scrollIndex = 0)
        val shortHeight = (60..400).last { frame.copy(windowHeight = it).visibleCount == 0 }
        val short = frame.copy(windowHeight = shortHeight)
        val fits = frame.copy(windowHeight = shortHeight + 1)

        assertEquals(1, fits.visibleCount)
        assertEquals(0, short.maximumScroll)
        assertEquals(0, short.scrollbar.maximumScroll)
        assertNull(short.optionAt(short.checkboxX + 1.0, short.rowsTop + 1.0))
    }

    /** 內容寬度最多 456 並置中；名稱在勾選方塊左邊，捲軸在內容右側的邊距內，清單在底部按鈕上方。 */
    @Test
    fun `content matches the settings form across window sizes`() {
        for (width in listOf(240, 320, 640, 1280)) {
            val layout = MultiChoiceLayout(width, 240, 8, 0)
            assertTrue(layout.contentWidth <= 456)
            assertTrue(abs(layout.contentLeft - (width - layout.contentRight)) <= 1)
            assertTrue(layout.contentLeft + layout.labelWidth < layout.checkboxX)
            assertEquals(layout.contentRight, layout.checkboxX + layout.checkboxSize)
            assertTrue(layout.scrollbarX > layout.contentRight)
            assertTrue(layout.scrollbar.trackBottom < layout.footerY)
            assertTrue(layout.countY < layout.rowsTop)
            assertEquals(layout.contentLeft, layout.footer.buttonX(MultiChoiceLayout.SELECT_ALL))
        }
    }

    /** 相鄰兩列的底色剛好銜接。 */
    @Test
    fun `row areas join up`() {
        val layout = MultiChoiceLayout(640, 360, 20, 0)
        assertEquals(layout.rowArea(0).bottom, layout.rowArea(1).top)
        assertTrue(layout.rowArea(0).top <= layout.rowTop(0))
    }

    /** 捲動後整列點擊對應同一個選項，捲軸及頁尾不會被當成選項。 */
    @Test
    fun `row hit testing uses the visible scroll offset and excludes the scrollbar`() {
        val layout = MultiChoiceLayout(640, 240, 20, 4)
        val y = layout.rowTop(0) + 10.0
        assertEquals(4, layout.optionAt(layout.contentLeft.toDouble(), y))
        assertEquals(4, layout.optionAt(layout.checkboxX + 10.0, y))
        assertNull(layout.optionAt(layout.scrollbarX + 1.0, y))
        assertTrue(layout.isOverScrollbar(layout.scrollbarX + 1.0, y))
        assertNull(layout.optionAt(layout.contentLeft.toDouble(), layout.footerY.toDouble()))
    }

    /** 清單最後一頁的空白列不能切換不存在的選項。 */
    @Test
    fun `unused rows do not select an option`() {
        val layout = MultiChoiceLayout(640, 360, 2, 0)
        assertEquals(0, layout.maximumScroll)
        assertNull(layout.optionAt(layout.contentLeft.toDouble(), layout.rowTop(2) + 2.0))
    }
}
