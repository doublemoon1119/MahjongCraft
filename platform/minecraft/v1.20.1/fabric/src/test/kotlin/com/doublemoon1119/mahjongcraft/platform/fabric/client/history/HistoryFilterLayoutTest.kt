package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import kotlin.test.Test
import kotlin.test.assertTrue

/** 驗證歷史篩選頁在不同高度下保留固定頁尾並以內容捲動承載所有列。 */
class HistoryFilterLayoutTest {
    /** 各測試高度的連續列不會互相重疊。 */
    @Test
    fun `rows remain non overlapping at supported heights`() {
        for (height in listOf(200, 230, 320)) {
            val layout = HistoryFilterLayout.create(height, 10)
            val first = layout.rowTop(0, 0)
            val second = layout.rowTop(1, 0)
            assertTrue(second >= first + layout.rowHeight)
            assertTrue(layout.footerTop > layout.headerBottom)
            assertTrue(layout.contentBottom < layout.footerTop)
        }
    }

    /** 超出可視區時應提供有限的內容捲動距離。 */
    @Test
    fun `overflow exposes bounded content scroll`() {
        val layout = HistoryFilterLayout.create(200, 10)
        assertTrue(layout.scrollableHeight > 0)
        assertTrue(layout.isVisible(0, 0))
        assertTrue(!layout.isVisible(0, layout.scrollableHeight))
    }

    /** 可完整顯示的列不會越過內容區下界。 */
    @Test
    fun `fully visible rows stay inside content bounds`() {
        for (height in listOf(200, 230, 320)) {
            val layout = HistoryFilterLayout.create(height, 4)
            repeat(4) { row ->
                assertTrue(layout.rowTop(row, 0) >= layout.contentTop)
                assertTrue(layout.rowTop(row, 0) + layout.rowHeight <= layout.contentBottom)
            }
        }
    }
}
