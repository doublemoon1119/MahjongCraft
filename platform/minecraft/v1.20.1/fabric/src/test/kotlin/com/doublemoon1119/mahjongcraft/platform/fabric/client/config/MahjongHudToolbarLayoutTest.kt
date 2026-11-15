package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.SettingsFooterButtons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證 HUD 編輯器工具列與底部按鈕列在寬窄視窗的排列。 */
class MahjongHudToolbarLayoutTest {
    /** 寬視窗四個控制項排成一列，由左到右不重疊並填滿內容寬度。 */
    @Test
    fun `wide screens put the four controls on one row`() {
        val layout = MahjongHudToolbarLayout(screenWidth = 480, screenHeight = 270)
        val controls = (0 until MahjongHudToolbarLayout.CONTROL_COUNT).map(layout::control)

        assertEquals(4, layout.columns)
        assertEquals(1, controls.map { it.y }.distinct().size)
        controls.zipWithNext().forEach { (left, right) -> assertTrue(left.right < right.x) }
        assertEquals(MahjongHudToolbarLayout.MARGIN, controls.first().x)
        assertEquals(480 - MahjongHudToolbarLayout.MARGIN, controls.last().right)
        assertTrue(controls.all { it.width >= MahjongHudToolbarLayout.MIN_CONTROL_WIDTH })
    }

    /** 窄視窗改成兩欄兩列：HUD 與情境在上，其他 HUD 與隱藏控制項在下。 */
    @Test
    fun `narrow screens use two columns and two rows`() {
        val layout = MahjongHudToolbarLayout(screenWidth = 320, screenHeight = 240)

        assertEquals(2, layout.columns)
        assertEquals(layout.control(MahjongHudToolbarLayout.HUD).y, layout.control(MahjongHudToolbarLayout.SCENARIO).y)
        assertEquals(layout.control(MahjongHudToolbarLayout.VISIBILITY).y, layout.control(MahjongHudToolbarLayout.HIDE_CONTROLS).y)
        assertTrue(layout.control(MahjongHudToolbarLayout.SCENARIO).bottom < layout.captionY(MahjongHudToolbarLayout.VISIBILITY))
        assertEquals(layout.control(MahjongHudToolbarLayout.HUD).x, layout.control(MahjongHudToolbarLayout.VISIBILITY).x)
        assertTrue(layout.toolbarBottom >= layout.control(MahjongHudToolbarLayout.HIDE_CONTROLS).bottom)
    }

    /** 標籤在控制項正上方，不和上一列的控制項重疊。 */
    @Test
    fun `captions sit above their controls`() {
        listOf(320 to 240, 480 to 270).forEach { (width, height) ->
            val layout = MahjongHudToolbarLayout(screenWidth = width, screenHeight = height)
            (0 until MahjongHudToolbarLayout.CONTROL_COUNT).forEach { index ->
                val caption = layout.captionY(index)
                assertTrue(caption < layout.control(index).y)
                assertTrue(caption >= MahjongHudToolbarLayout.TOP)
            }
        }
    }

    /** 底部按鈕和工具列用同樣的欄數，返回固定在右下角，最後一列貼近視窗底部。 */
    @Test
    fun `footer follows the toolbar columns with back in the bottom right`() {
        listOf(320 to 240, 480 to 270).forEach { (width, height) ->
            val layout = MahjongHudToolbarLayout(screenWidth = width, screenHeight = height)
            val buttons = (0 until SettingsFooterButtons.COUNT).map(layout::footerButton)
            val back = layout.footerButton(SettingsFooterButtons.DONE)

            assertEquals(buttons.maxOf { it.right }, back.right)
            assertEquals(buttons.maxOf { it.bottom }, back.bottom)
            assertEquals(height - MahjongHudToolbarLayout.FOOTER_BOTTOM_MARGIN, back.bottom)
            assertEquals(layout.rows, buttons.map { it.y }.distinct().size)
            assertTrue(layout.footerTop > layout.toolbarBottom)
        }
    }

    /** 極小的視窗仍能建立版面，底部按鈕不會跑到工具列上方。 */
    @Test
    fun `tiny screens stay computable`() {
        val layout = MahjongHudToolbarLayout(screenWidth = 1, screenHeight = 1)

        assertEquals(2, layout.columns)
        assertTrue(layout.footerTop >= layout.toolbarBottom)
        assertTrue((0 until MahjongHudToolbarLayout.CONTROL_COUNT).all { layout.control(it).width > 0 })
    }
}
