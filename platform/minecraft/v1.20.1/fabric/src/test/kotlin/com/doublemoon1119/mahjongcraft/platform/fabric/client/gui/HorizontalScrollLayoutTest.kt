package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證連續像素水平捲動的 thumb 幾何與拖曳、滾輪換算。 */
class HorizontalScrollLayoutTest {
    private fun layout(contentWidth: Int, viewportWidth: Int = 100, viewportLeft: Int = 10) = HorizontalScrollLayout(
        viewportLeft = viewportLeft,
        viewportWidth = viewportWidth,
        contentWidth = contentWidth,
        minimumThumbWidth = 18,
    )

    /** 內容沒有超出可見寬度時不需要捲動，thumb 佔滿整條軌道。 */
    @Test
    fun `content that fits needs no scrolling`() {
        val layout = layout(contentWidth = 80)

        assertEquals(0.0, layout.maximumScroll)
        assertEquals(10, layout.thumb(0.0).left)
        assertEquals(100, layout.thumb(0.0).width)
        assertEquals(0.0, layout.scrollFromDrag(startScroll = 0.0, pointerDelta = 50.0))
        assertEquals(0.0, layout.scrollFromWheel(currentScroll = 0.0, amount = -1.0, step = 48.0))
    }

    /** thumb 寬度依可見比例縮放，位置依捲動比例移動。 */
    @Test
    fun `thumb scales with the visible ratio and follows the scroll`() {
        val layout = layout(contentWidth = 200)

        val atStart = layout.thumb(0.0)
        val atEnd = layout.thumb(layout.maximumScroll)

        assertEquals(100.0, layout.maximumScroll)
        assertEquals(50, atStart.width)
        assertEquals(10, atStart.left)
        assertEquals(50, atEnd.width)
        assertEquals(60, atEnd.left, "thumb should reach the right edge of the viewport")
    }

    /** thumb 不會小於最小寬度，也不會超出可見寬度。 */
    @Test
    fun `thumb stays within its minimum and the viewport`() {
        assertEquals(18, layout(contentWidth = 10_000).thumb(0.0).width)
        assertEquals(10, layout(contentWidth = 10_000, viewportWidth = 10).thumb(0.0).width, "a viewport narrower than the minimum wins")
    }

    /** 由 thumb 左界換算回捲動量，超出軌道的位置夾在兩端。 */
    @Test
    fun `scroll from a thumb position clamps to the track`() {
        val layout = layout(contentWidth = 200)

        assertEquals(0.0, layout.scrollFromThumbLeft(thumbLeft = 10.0))
        assertEquals(100.0, layout.scrollFromThumbLeft(thumbLeft = 60.0))
        assertEquals(0.0, layout.scrollFromThumbLeft(thumbLeft = -50.0))
        assertEquals(100.0, layout.scrollFromThumbLeft(thumbLeft = 500.0))
        assertTrue(layout.scrollFromThumbLeft(thumbLeft = 35.0) in 40.0..60.0, "half way along the track is half the content")
    }

    /** 拖曳位移依軌道比例換算，並夾在合法範圍內。 */
    @Test
    fun `dragging converts the pointer delta into scroll`() {
        val layout = layout(contentWidth = 200)

        assertEquals(100.0, layout.scrollFromDrag(startScroll = 0.0, pointerDelta = 50.0))
        assertEquals(50.0, layout.scrollFromDrag(startScroll = 0.0, pointerDelta = 25.0))
        assertEquals(0.0, layout.scrollFromDrag(startScroll = 50.0, pointerDelta = -500.0))
        assertEquals(100.0, layout.scrollFromDrag(startScroll = 50.0, pointerDelta = 500.0))
    }

    /** 滾輪往前捲動內容向右，往後回到起點，兩端都夾住。 */
    @Test
    fun `the wheel scrolls by one step and clamps at both ends`() {
        val layout = layout(contentWidth = 200)

        assertEquals(48.0, layout.scrollFromWheel(currentScroll = 0.0, amount = -1.0, step = 48.0))
        assertEquals(0.0, layout.scrollFromWheel(currentScroll = 48.0, amount = 1.0, step = 48.0))
        assertEquals(0.0, layout.scrollFromWheel(currentScroll = 0.0, amount = 1.0, step = 48.0))
        assertEquals(100.0, layout.scrollFromWheel(currentScroll = 80.0, amount = -1.0, step = 48.0))
    }
}
