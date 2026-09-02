package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證設定 footer 在 Minecraft 最小 scaled width 下仍維持單行。 */
class SettingsFooterLayoutTest {
    /** 296px 面板扣除內距後，四顆按鈕不得溢出或重疊。 */
    @Test
    fun `minimum client panel keeps every footer button on one line`() {
        val layout = SettingsFooterLayout.create(left = 10, availableWidth = 276, preferredResetWidth = 104, gap = 6)

        assertTrue(layout.actionWidth >= 50)
        assertTrue(layout.undoX >= layout.resetX + layout.resetWidth)
        assertTrue(layout.applyX >= layout.undoX + layout.actionWidth)
        assertTrue(layout.doneX >= layout.applyX + layout.actionWidth)
        assertEquals(285, layout.right)
    }
}
