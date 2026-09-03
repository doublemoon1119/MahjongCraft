package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 驗證 HUD 位置編輯器頂部工具列的溢出判定、scrollbar 換算與各點擊區域互不重疊。
 */
class MahjongHudToolbarLayoutTest {
    /** 內容未超出可見寬度時不出現 scrollbar，也沒有可捲動距離。 */
    @Test
    fun `scrollbar only appears once the toolbar content overflows`() {
        val fitting = layout(contentWidth = 200)
        assertFalse(fitting.hasOverflow)
        assertEquals(0.0, fitting.maximumScroll)

        val overflowing = layout(contentWidth = fitting.viewportWidth + 1)
        assertTrue(overflowing.hasOverflow)
        assertEquals(1.0, overflowing.maximumScroll)
    }

    /** 未溢出時整組按鈕在可見範圍內置中，溢出時改為依捲動量靠左對齊。 */
    @Test
    fun `content is centered while it fits and scrolls once it overflows`() {
        val fitting = layout(contentWidth = 200)
        val expectedCentered = MahjongHudToolbarLayout.MARGIN + (fitting.viewportWidth - 200) / 2
        assertEquals(expectedCentered, fitting.contentOffset(0.0))

        val overflowing = layout(contentWidth = 2_000)
        assertEquals(MahjongHudToolbarLayout.MARGIN, overflowing.contentOffset(0.0))
        assertEquals(MahjongHudToolbarLayout.MARGIN - 30, overflowing.contentOffset(30.0))
    }

    /** thumb 在兩端分別貼齊軌道起點與終點，且寬度不小於最小可視寬度。 */
    @Test
    fun `scrollbar thumb spans the track from end to end`() {
        val toolbar = layout(contentWidth = 2_000)
        val atStart = toolbar.thumb(0.0)
        val atEnd = toolbar.thumb(toolbar.maximumScroll)

        assertEquals(MahjongHudToolbarLayout.MARGIN, atStart.left)
        assertTrue(atStart.width >= MahjongHudToolbarLayout.MIN_THUMB_WIDTH)
        assertEquals(atStart.width, atEnd.width)
        assertEquals(MahjongHudToolbarLayout.MARGIN + toolbar.viewportWidth, atEnd.right)
    }

    /** 由 thumb 位置換算回的捲動量與原捲動量一致，只允許整數像素造成的誤差。 */
    @Test
    fun `thumb position round trips back to the same scroll amount`() {
        val toolbar = layout(contentWidth = 2_000)
        val travel = (toolbar.viewportWidth - toolbar.thumb(0.0).width).coerceAtLeast(1)
        val pixelTolerance = toolbar.maximumScroll / travel

        listOf(0.0, toolbar.maximumScroll / 2, toolbar.maximumScroll).forEach { scroll ->
            val restored = toolbar.scrollFromThumb(toolbar.thumb(scroll).left.toDouble())
            assertTrue(
                abs(restored - scroll) <= pixelTolerance,
                "scroll $scroll restored as $restored",
            )
        }
    }

    /** 拖曳與滾輪換算出的捲動量都被限制在合法範圍內。 */
    @Test
    fun `drag and wheel scrolling stay within the legal range`() {
        val toolbar = layout(contentWidth = 2_000)

        assertEquals(0.0, toolbar.scrollFromDrag(startScroll = 0.0, pointerDelta = -10_000.0))
        assertEquals(toolbar.maximumScroll, toolbar.scrollFromDrag(startScroll = 0.0, pointerDelta = 10_000.0))
        assertEquals(0.0, toolbar.scrollFromWheel(currentScroll = 0.0, amount = 1.0))
        assertEquals(toolbar.maximumScroll, toolbar.scrollFromWheel(currentScroll = 0.0, amount = -10_000.0))
    }

    /** 按鈕列與 scrollbar 是互斥的點擊區域，兩者都落在整體工具列區域之內。 */
    @Test
    fun `button row and scrollbar never claim the same pointer position`() {
        val toolbar = layout(contentWidth = 2_000)
        val insideX = MahjongHudToolbarLayout.MARGIN + 1.0
        val buttonY = MahjongHudToolbarLayout.TOP + 1.0
        val scrollbarY = MahjongHudToolbarLayout.SCROLLBAR_TOP + 1.0

        assertTrue(toolbar.isInsideButtons(insideX, buttonY))
        assertFalse(toolbar.isOverScrollbar(insideX, buttonY))
        assertTrue(toolbar.isOverScrollbar(insideX, scrollbarY))
        assertFalse(toolbar.isInsideButtons(insideX, scrollbarY))
        assertTrue(toolbar.isInsideArea(insideX, buttonY))
        assertTrue(toolbar.isInsideArea(insideX, scrollbarY))
    }

    /** 固定按鈕所在的右側區域不屬於可捲動工具列，不會攔截其點擊。 */
    @Test
    fun `the fixed right hand controls sit outside the scrolling viewport`() {
        val toolbar = layout(contentWidth = 2_000)
        val fixedControlsX = toolbar.viewportRight + 1.0
        val buttonY = MahjongHudToolbarLayout.TOP + 1.0

        assertFalse(toolbar.isInsideButtons(fixedControlsX, buttonY))
        assertFalse(toolbar.isInsideArea(fixedControlsX, buttonY))
        assertFalse(toolbar.isOverScrollbar(fixedControlsX, MahjongHudToolbarLayout.SCROLLBAR_TOP + 1.0))
        assertTrue(toolbar.viewportRight < SCREEN_WIDTH - MahjongHudToolbarLayout.HIDE_CONTROLS_WIDTH)
    }

    /** 下拉選單依序對應每一列，超出項目數量或落在 popup 外都不算命中。 */
    @Test
    fun `dropdown clicks map to the row under the pointer`() {
        val toolbar = layout(contentWidth = 400)
        val anchorX = MahjongHudToolbarLayout.MARGIN
        val left = toolbar.dropdownLeft(anchorX)
        val optionCount = 3

        repeat(optionCount) { index ->
            assertEquals(
                index,
                toolbar.dropdownOptionIndexAt(
                    mouseX = left + 1.0,
                    mouseY = MahjongHudToolbarLayout.POPUP_TOP +
                        index * MahjongHudToolbarLayout.DROPDOWN_OPTION_HEIGHT + 1.0,
                    anchorX = anchorX,
                    optionCount = optionCount,
                ),
            )
        }

        assertNull(
            toolbar.dropdownOptionIndexAt(
                mouseX = left + 1.0,
                mouseY = MahjongHudToolbarLayout.POPUP_TOP - 1.0,
                anchorX = anchorX,
                optionCount = optionCount,
            ),
        )
        assertNull(
            toolbar.dropdownOptionIndexAt(
                mouseX = left + 1.0,
                mouseY = MahjongHudToolbarLayout.POPUP_TOP +
                    optionCount * MahjongHudToolbarLayout.DROPDOWN_OPTION_HEIGHT + 1.0,
                anchorX = anchorX,
                optionCount = optionCount,
            ),
        )
        assertNull(
            toolbar.dropdownOptionIndexAt(
                mouseX = left - 1.0,
                mouseY = MahjongHudToolbarLayout.POPUP_TOP + 1.0,
                anchorX = anchorX,
                optionCount = optionCount,
            ),
        )
    }

    /** 錨點靠近右緣時下拉選單左界內縮，確保 popup 完整留在畫面內。 */
    @Test
    fun `dropdown popups are kept inside the screen`() {
        val toolbar = layout(contentWidth = 400)
        val left = toolbar.dropdownLeft(SCREEN_WIDTH)

        assertTrue(left >= MahjongHudToolbarLayout.MARGIN)
        assertTrue(left + MahjongHudToolbarLayout.DROPDOWN_POPUP_WIDTH <= SCREEN_WIDTH)
    }

    /**
     * 最小支援解析度搭配高 GUI scale 時，可捲動 viewport 會窄於 thumb 最小寬度；此時 thumb 計算
     * 仍必須產生合法範圍，不得因為下界大於上界而丟出例外。
     */
    @Test
    fun `high gui scales at the smallest resolution keep the scrollbar computable`() {
        listOf(SCREEN_WIDTH, SCREEN_WIDTH / 2, SCREEN_WIDTH / 3, SCREEN_WIDTH / 4).forEach { scaledWidth ->
            val toolbar = MahjongHudToolbarLayout(
                screenWidth = scaledWidth,
                contentWidth = OVERFLOWING_CONTENT_WIDTH,
            )
            val thumb = toolbar.thumb(toolbar.maximumScroll)

            assertTrue(thumb.width >= 1, "scaled width $scaledWidth produced thumb width ${thumb.width}")
            assertTrue(
                thumb.width <= toolbar.viewportWidth,
                "scaled width $scaledWidth produced a thumb wider than its viewport",
            )
            assertTrue(
                thumb.right <= MahjongHudToolbarLayout.MARGIN + toolbar.viewportWidth,
                "scaled width $scaledWidth pushed the thumb past its track",
            )
        }
    }

    /** 極窄畫面下 viewport 仍保留正寬度，scrollbar 計算不得產生非法數值。 */
    @Test
    fun `narrow screens still produce a usable viewport`() {
        val toolbar = MahjongHudToolbarLayout(screenWidth = 200, contentWidth = 400)

        assertTrue(toolbar.viewportWidth >= 1)
        assertTrue(toolbar.maximumScroll >= 0.0)
        assertTrue(toolbar.thumb(0.0).width >= 1)
        assertTrue(toolbar.scrollFromThumb(0.0) in 0.0..toolbar.maximumScroll)
    }

    /** 沒有任何工具列按鈕時不得因為除以零而算出非法 thumb。 */
    @Test
    fun `an empty toolbar reports no overflow and a full width thumb`() {
        val toolbar = layout(contentWidth = 0)

        assertFalse(toolbar.hasOverflow)
        assertEquals(0.0, toolbar.maximumScroll)
        assertEquals(toolbar.viewportWidth, toolbar.thumb(0.0).width)
    }

    /** 以最小支援畫面寬度建立工具列幾何。 */
    private fun layout(contentWidth: Int): MahjongHudToolbarLayout = MahjongHudToolbarLayout(
        screenWidth = SCREEN_WIDTH,
        contentWidth = contentWidth,
    )

    private companion object {
        /** 測試使用的最小支援畫面寬度。 */
        const val SCREEN_WIDTH = 854

        /** 實際工具列在任何支援解析度下都會溢出的內容寬度（兩顆下拉按鈕加間距）。 */
        const val OVERFLOWING_CONTENT_WIDTH =
            MahjongHudToolbarLayout.DROPDOWN_WIDTH * 2 + MahjongHudToolbarLayout.GAP
    }
}
