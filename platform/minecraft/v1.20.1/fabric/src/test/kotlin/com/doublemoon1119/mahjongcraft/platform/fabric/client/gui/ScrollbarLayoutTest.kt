package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證 scrollbar 的繪製與拖曳使用相同座標系。 */
class ScrollbarLayoutTest {
    /** 抓住 thumb 中段時不得跳到以游標為 thumb 上界的位置。 */
    @Test
    fun `drag preserves the point grabbed inside the thumb`() {
        val initial = ScrollbarLayout(20, 120, 10, 4, 3, 12)
        val grabOffset = initial.grabOffset(initial.thumbTop + 10.0)

        assertEquals(3, initial.scrollIndexFor(initial.thumbTop + 10.0, grabOffset))
        assertEquals(5, initial.scrollIndexFor(80.0, grabOffset))
    }

    /** 軌道比最小滑塊短時，滑塊仍限制在軌道內。 */
    @Test
    fun `short scroll tracks bound the minimum thumb height`() {
        val layout = ScrollbarLayout(
            trackTop = 20,
            trackBottom = 27,
            itemCount = 10,
            visibleItemCount = 1,
            scrollIndex = 3,
            minimumThumbHeight = 12,
        )

        assertEquals(9, layout.maximumScroll)
        assertEquals(7, layout.thumbHeight)
        assertEquals(20, layout.thumbTop)
    }

    /** 軌道兩端必須穩定映射到合法範圍。 */
    @Test
    fun `drag clamps to both ends of the track`() {
        val layout = ScrollbarLayout(20, 120, 10, 4, 0, 12)

        assertEquals(0, layout.scrollIndexFor(-100.0, layout.thumbHeight / 2.0))
        assertEquals(6, layout.scrollIndexFor(500.0, layout.thumbHeight / 2.0))
    }
}
