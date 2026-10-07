package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.MarqueeTiming.MILLIS_PER_PIXEL
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.MarqueeTiming.START_PAUSE_MILLIS
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證提示框在畫面上下緣的推回與捲動。 */
class ScreenFittingTooltipPositionerTest {
    /** 原本位置放得下時不調整。 */
    @Test
    fun `fitting position is unchanged`() {
        assertEquals(50, fitTooltipY(preferredY = 50, tooltipHeight = 100, screenHeight = 240, elapsedMillis = 0))
    }

    /** 超出上緣或下緣時推回畫面內，背景邊緣也留在畫面內。 */
    @Test
    fun `overflowing positions are pushed back inside the screen`() {
        assertEquals(TOOLTIP_BACKGROUND_MARGIN, fitTooltipY(preferredY = -30, tooltipHeight = 200, screenHeight = 240, elapsedMillis = 0))
        assertEquals(240 - TOOLTIP_BACKGROUND_MARGIN - 200, fitTooltipY(preferredY = 100, tooltipHeight = 200, screenHeight = 240, elapsedMillis = 0))
    }

    /** 比畫面還高時從上緣開始，捲到底時下緣剛好露出。 */
    @Test
    fun `taller than the screen scrolls from top to bottom`() {
        val height = 260
        val overflow = height + TOOLTIP_BACKGROUND_MARGIN * 2 - 240

        assertEquals(TOOLTIP_BACKGROUND_MARGIN, fitTooltipY(preferredY = -50, tooltipHeight = height, screenHeight = 240, elapsedMillis = 0))
        val bottom = fitTooltipY(
            preferredY = -50,
            tooltipHeight = height,
            screenHeight = 240,
            elapsedMillis = START_PAUSE_MILLIS + overflow * MILLIS_PER_PIXEL,
        )
        assertEquals(240 - TOOLTIP_BACKGROUND_MARGIN, bottom + height)
    }
}
