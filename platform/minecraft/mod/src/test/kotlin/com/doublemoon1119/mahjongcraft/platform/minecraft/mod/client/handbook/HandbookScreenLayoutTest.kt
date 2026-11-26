package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.handbook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證麻將手冊快捷畫面的入口寬度、置中、提示與捲動。 */
class HandbookScreenLayoutTest {
    /** 寬版的入口寬 300 並置中，三個入口不必捲動，提示在入口與底部按鈕之間。 */
    @Test
    fun `wide windows center fixed-width entries and show the hint`() {
        val layout = HandbookScreenLayout(windowWidth = 480, windowHeight = 270, entryCount = 3, scrollIndex = 0)

        assertEquals(HandbookScreenLayout.ENTRY_MAX_WIDTH, layout.entryWidth)
        assertEquals((480 - layout.entryWidth) / 2, layout.entryLeft)
        assertTrue(layout.visibleCount >= 3)
        assertEquals(0, layout.maximumScroll)
        assertTrue(layout.showsOpenHint)
        assertTrue(layout.entryTop(2) + HandbookScreenLayout.ENTRY_HEIGHT < layout.openHintY)
        assertTrue(layout.openHintY < layout.footerY)
    }

    /** 窄版的入口佔滿內容寬度，不顯示開啟方式的提示。 */
    @Test
    fun `narrow windows stretch entries and hide the hint`() {
        val layout = HandbookScreenLayout(windowWidth = 320, windowHeight = 240, entryCount = 3, scrollIndex = 0)

        assertEquals(layout.contentWidth, layout.entryWidth)
        assertFalse(layout.showsOpenHint)
        assertTrue(layout.entryTop(layout.visibleCount - 1) + HandbookScreenLayout.ENTRY_HEIGHT < layout.footerY)
    }

    /** 放不下全部入口時可以捲動，捲軸在入口右側，關閉按鈕固定在右下角。 */
    @Test
    fun `short windows scroll the entries`() {
        val layout = HandbookScreenLayout(windowWidth = 480, windowHeight = 140, entryCount = 4, scrollIndex = 0)

        assertTrue(layout.visibleCount in 1 until 4)
        assertEquals(4 - layout.visibleCount, layout.maximumScroll)
        assertTrue(layout.scrollbarX >= layout.entryLeft + layout.entryWidth)
        assertTrue(layout.isOverScrollableArea(layout.entryLeft + 1.0, layout.entriesTop + 1.0))
        assertTrue(layout.scrollbar.trackBottom < layout.footerY)
        assertEquals(
            expected = layout.footer.buttonX(HandbookScreenLayout.CLOSE) + layout.footer.buttonWidth(HandbookScreenLayout.CLOSE),
            actual = layout.footer.buttonX(0) + layout.footer.width,
        )
    }

    /** 相鄰入口之間保留間距，不互相重疊。 */
    @Test
    fun `entries keep a gap between rows`() {
        val layout = HandbookScreenLayout(windowWidth = 480, windowHeight = 270, entryCount = 3, scrollIndex = 0)

        assertTrue(layout.entryTop(1) - layout.entryTop(0) > HandbookScreenLayout.ENTRY_HEIGHT)
    }
}
