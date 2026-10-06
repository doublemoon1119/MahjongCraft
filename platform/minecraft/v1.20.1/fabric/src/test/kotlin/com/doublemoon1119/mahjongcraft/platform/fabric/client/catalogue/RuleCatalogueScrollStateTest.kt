package com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證規則一覽內容區的滾輪、捲軸拖曳與重設行為。 */
class RuleCatalogueScrollStateTest {
    /** 測試用畫面幾何。 */
    private val layout = RuleCatalogueScreenLayout.measure(
        width = 320,
        height = 240,
        clearWidth = 36,
        sourceToggle = false,
    )

    /** 超出一頁的內容高度。 */
    private val contentHeight = layout.contentBounds.height * 4

    /** 滾輪在頂端與底端都停住，不會超出範圍。 */
    @Test
    fun `wheel stops at both ends`() {
        val state = RuleCatalogueScrollState()

        state.scrollBy(amount = 1.0, layout = layout, contentHeight = contentHeight)
        assertEquals(0.0, state.offset)
        state.scrollBy(amount = -1.0, layout = layout, contentHeight = contentHeight)
        assertEquals(RuleCatalogueScrollState.SCROLL_STEP.toDouble(), state.offset)
        state.scrollBy(amount = -1000.0, layout = layout, contentHeight = contentHeight)
        assertEquals(layout.maximumScroll(contentHeight).toDouble(), state.offset)
    }

    /** 內容不超過一頁時按下捲軸不會開始拖曳。 */
    @Test
    fun `short content cannot be dragged`() {
        val state = RuleCatalogueScrollState()

        assertFalse(state.press(mouseY = layout.scrollbarBounds.y + 5.0, layout = layout, contentHeight = 10))
        assertFalse(state.isDragging)
    }

    /** 按在滑塊上拖曳時保留抓取位置，不會先跳一下。 */
    @Test
    fun `dragging the thumb keeps the grab position`() {
        val state = RuleCatalogueScrollState()
        val thumbTop = layout.scrollbar(contentHeight = contentHeight, scroll = 0.0).thumbTop

        assertTrue(state.press(mouseY = thumbTop + 3.0, layout = layout, contentHeight = contentHeight))
        assertEquals(0.0, state.offset)
        assertTrue(state.drag(mouseY = thumbTop + 3.0, layout = layout, contentHeight = contentHeight))
        assertEquals(0.0, state.offset)
    }

    /** 按在軌道空白處時滑塊中心移到游標位置。 */
    @Test
    fun `pressing the track jumps toward the pointer`() {
        val state = RuleCatalogueScrollState()

        assertTrue(state.press(mouseY = layout.scrollbarBounds.bottom - 1.0, layout = layout, contentHeight = contentHeight))
        assertTrue(state.offset > 0.0)
    }

    /** 拖曳到捲軸外仍持續跟隨並停在邊界，放開後不再跟隨。 */
    @Test
    fun `dragging continues outside the track until release`() {
        val state = RuleCatalogueScrollState()
        val thumbTop = layout.scrollbar(contentHeight = contentHeight, scroll = 0.0).thumbTop

        state.press(mouseY = thumbTop + 1.0, layout = layout, contentHeight = contentHeight)
        state.drag(mouseY = layout.height + 500.0, layout = layout, contentHeight = contentHeight)
        assertEquals(layout.maximumScroll(contentHeight).toDouble(), state.offset)
        state.drag(mouseY = -500.0, layout = layout, contentHeight = contentHeight)
        assertEquals(0.0, state.offset)
        assertTrue(state.release())
        assertFalse(state.drag(mouseY = layout.height.toDouble(), layout = layout, contentHeight = contentHeight))
        assertEquals(0.0, state.offset)
        assertFalse(state.release())
    }

    /** 篩選改變時回到頂端並結束拖曳。 */
    @Test
    fun `reset returns to top and stops dragging`() {
        val state = RuleCatalogueScrollState()
        state.scrollBy(amount = -3.0, layout = layout, contentHeight = contentHeight)
        state.press(mouseY = layout.scrollbarBounds.y + 1.0, layout = layout, contentHeight = contentHeight)

        state.reset()

        assertEquals(0.0, state.offset)
        assertFalse(state.isDragging)
    }

    /** 視窗尺寸改變時保留位置但限制在新範圍內，並結束拖曳。 */
    @Test
    fun `relayout keeps the offset within the new range`() {
        val state = RuleCatalogueScrollState()
        state.scrollBy(amount = -1000.0, layout = layout, contentHeight = contentHeight)
        state.press(mouseY = layout.scrollbarBounds.bottom - 1.0, layout = layout, contentHeight = contentHeight)
        val taller = RuleCatalogueScreenLayout.measure(
            width = 320,
            height = 480,
            clearWidth = 36,
            sourceToggle = false,
        )

        state.relayout(layout = taller, contentHeight = contentHeight)

        assertEquals(taller.maximumScroll(contentHeight).toDouble(), state.offset)
        assertFalse(state.isDragging)
        state.scrollBy(amount = -1.0, layout = layout, contentHeight = contentHeight)
        state.relayout(layout = layout, contentHeight = layout.contentBounds.height)
        assertEquals(0.0, state.offset)
    }
}
