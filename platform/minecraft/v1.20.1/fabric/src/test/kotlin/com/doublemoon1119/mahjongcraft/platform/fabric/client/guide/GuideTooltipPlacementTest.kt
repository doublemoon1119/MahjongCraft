package com.doublemoon1119.mahjongcraft.platform.fabric.client.guide

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.TOOLTIP_BACKGROUND_MARGIN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證場景提示框避開指著的東西與游標，並留在畫面內。 */
class GuideTooltipPlacementTest {
    /** 上方放得下時放在指著的東西上方，水平以游標為中心。 */
    @Test
    fun `tooltips sit above the hovered thing`() {
        val position = GuideTooltipPlacement(anchorTop = 100, anchorBottom = 140).positionTooltip(SCREEN_WIDTH, SCREEN_HEIGHT, 200, 120, 80, 30)

        assertEquals(160, position.x())
        assertEquals(100 - GuideTooltipPlacement.GAP - TOOLTIP_BACKGROUND_MARGIN - 30, position.y())
    }

    /** 上方放不下時改放指著的東西下方，不蓋住它。 */
    @Test
    fun `tooltips move below when there is no room above`() {
        val position = GuideTooltipPlacement(anchorTop = 20, anchorBottom = 60).positionTooltip(SCREEN_WIDTH, SCREEN_HEIGHT, 200, 40, 80, 30)

        assertEquals(60 + GuideTooltipPlacement.GAP + TOOLTIP_BACKGROUND_MARGIN, position.y())
    }

    /** 指著的東西佔滿畫面高度時，改放在游標上方，不蓋住游標。 */
    @Test
    fun `tooltips avoid the cursor when the hovered thing fills the screen`() {
        val position = GuideTooltipPlacement(anchorTop = 0, anchorBottom = SCREEN_HEIGHT).positionTooltip(SCREEN_WIDTH, SCREEN_HEIGHT, 200, 150, 80, 30)

        val bottom = position.y() + 30 + TOOLTIP_BACKGROUND_MARGIN
        assertTrue(bottom <= 150 - GuideTooltipPlacement.CURSOR_GAP)
    }

    /** 靠近畫面邊緣時水平往內推，提示框背景不超出畫面。 */
    @Test
    fun `tooltips stay inside the screen horizontally`() {
        val placement = GuideTooltipPlacement(anchorTop = 100, anchorBottom = 140)

        assertEquals(TOOLTIP_BACKGROUND_MARGIN, placement.positionTooltip(SCREEN_WIDTH, SCREEN_HEIGHT, 5, 120, 80, 30).x())
        assertEquals(SCREEN_WIDTH - 80 - TOOLTIP_BACKGROUND_MARGIN, placement.positionTooltip(SCREEN_WIDTH, SCREEN_HEIGHT, SCREEN_WIDTH - 5, 120, 80, 30).x())
    }

    /** 測試用畫面尺寸。 */
    private companion object {
        /** 畫面寬度。 */
        const val SCREEN_WIDTH = 480

        /** 畫面高度。 */
        const val SCREEN_HEIGHT = 270
    }
}
