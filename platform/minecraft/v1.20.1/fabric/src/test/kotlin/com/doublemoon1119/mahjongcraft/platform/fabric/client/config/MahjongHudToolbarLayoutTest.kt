package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證 HUD 位置編輯器第二行（二級選項）的水平捲動幾何。 */
class MahjongHudToolbarLayoutTest {
    /** 內容未超出可見寬度時不出現 scrollbar，也沒有可捲動距離。 */
    @Test
    fun `scrollbar only appears once the row content overflows`() {
        val fitting = layout(contentWidth = 200)
        assertFalse(fitting.hasOverflow)
        assertEquals(0.0, fitting.maximumScroll)

        val overflowing = layout(contentWidth = fitting.viewportWidth + 1)
        assertTrue(overflowing.hasOverflow)
        assertEquals(1.0, overflowing.maximumScroll)
    }

    /** 內容一律靠左對齊，不論是否溢出；溢出時再依捲動量往左捲動。 */
    @Test
    fun `content is left-aligned whether or not it overflows`() {
        val fitting = layout(contentWidth = 200)
        assertEquals(MahjongHudToolbarLayout.MARGIN, fitting.contentOffset(0.0))

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

    /**
     * 最小支援解析度搭配高 GUI scale 時，可捲動 viewport 會窄於 thumb 最小寬度；此時 thumb 計算
     * 仍必須產生合法範圍，不得因為下界大於上界而丟出例外。
     */
    @Test
    fun `high gui scales at the smallest resolution keep the scrollbar computable`() {
        listOf(SCREEN_WIDTH, SCREEN_WIDTH / 2, SCREEN_WIDTH / 3, SCREEN_WIDTH / 4).forEach { scaledWidth ->
            val toolbar = MahjongHudToolbarLayout(screenWidth = scaledWidth, contentWidth = OVERFLOWING_CONTENT_WIDTH)
            val thumb = toolbar.thumb(toolbar.maximumScroll)

            assertTrue(thumb.width >= 1, "scaled width $scaledWidth produced thumb width ${thumb.width}")
            assertTrue(thumb.width <= toolbar.viewportWidth, "scaled width $scaledWidth produced a thumb wider than its viewport")
            assertTrue(thumb.right <= MahjongHudToolbarLayout.MARGIN + toolbar.viewportWidth, "scaled width $scaledWidth pushed the thumb past its track")
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

    /** 沒有任何二級選項按鈕時不得因為除以零而算出非法 thumb。 */
    @Test
    fun `an empty row reports no overflow and a full width thumb`() {
        val toolbar = layout(contentWidth = 0)

        assertFalse(toolbar.hasOverflow)
        assertEquals(0.0, toolbar.maximumScroll)
        assertEquals(toolbar.viewportWidth, toolbar.thumb(0.0).width)
    }

    private fun layout(contentWidth: Int): MahjongHudToolbarLayout = MahjongHudToolbarLayout(
        screenWidth = SCREEN_WIDTH,
        contentWidth = contentWidth,
    )

    private companion object {
        /** 測試使用的最小支援畫面寬度。 */
        const val SCREEN_WIDTH = 854

        /** 實際二級選項行在任何支援解析度下都會溢出的內容寬度。 */
        const val OVERFLOWING_CONTENT_WIDTH = MahjongHudToolbarLayout.SELECTOR_WIDTH * 5
    }
}
