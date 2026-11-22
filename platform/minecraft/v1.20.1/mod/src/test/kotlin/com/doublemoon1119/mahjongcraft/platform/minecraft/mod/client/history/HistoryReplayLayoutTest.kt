package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證牌譜回放畫面在寬窄視窗與全螢幕的版面不重疊、不超出視窗。 */
class HistoryReplayLayoutTest {
    /** 寬視窗：場景在左、右欄在右，進度條與控制列在兩者下方、底部按鈕上方。 */
    @Test
    fun `wide layout keeps the side column beside the scene`() {
        val layout = HistoryReplayLayout.measure(width = 480, height = 270, labelWidth = LABEL_WIDTH, speedSelectorWidth = SPEED_WIDTH)

        assertTrue(layout.wide)
        assertNull(layout.listToggle)
        assertTrue(layout.scene.right <= layout.viewSelector.x - LABEL_WIDTH)
        assertTrue(layout.scene.right <= layout.list.x)
        assertTrue(layout.list.y >= layout.handSelector.bottom)
        assertTrue(layout.list.bottom <= layout.progress.y)
        assertTrue(layout.caption.bottom <= layout.progress.y)
        assertTrue(layout.progress.bottom <= layout.controls.y)
        assertTrue(layout.controls.bottom <= layout.footerTop)
        assertTrue(layout.listScrollbarX < 480)
        assertTrue(layout.controlButton(HistoryReplayLayout.CONTROL_BUTTONS - 1).right < layout.speedSelector().x)
    }

    /** 窄視窗：場景佔滿寬度，選擇器與交易列表疊在場景裡，交易列表由按鈕叫出。 */
    @Test
    fun `narrow layout overlays selectors and the list on the scene`() {
        val layout = HistoryReplayLayout.measure(width = 360, height = 240, labelWidth = LABEL_WIDTH, speedSelectorWidth = SPEED_WIDTH)

        assertFalse(layout.wide)
        assertNotNull(layout.listToggle)
        assertTrue(layout.viewSelector.x >= layout.scene.x && layout.handSelector.bottom <= layout.scene.bottom)
        assertTrue(layout.list.x >= layout.scene.x && layout.list.bottom <= layout.scene.bottom)
        assertTrue(layout.list.x > layout.viewSelector.right)
        assertTrue(layout.list.y >= layout.fullscreenToggle.bottom)
        assertTrue(layout.controls.bottom <= layout.footerTop)
    }

    /** 全螢幕：場景佔滿視窗，進度條與播放控制列都在底部的控制區內，全螢幕按鈕在場景右上角。 */
    @Test
    fun `fullscreen layout fills the window and keeps controls in the bottom bar`() {
        val layout = HistoryReplayLayout.fullscreen(width = 480, height = 270, speedSelectorWidth = SPEED_WIDTH)
        val bar = assertNotNull(layout.controlBar)

        assertTrue(layout.fullscreen)
        assertTrue(layout.scene.x == 0 && layout.scene.y == 0 && layout.scene.right == 480 && layout.scene.bottom == 270)
        assertTrue(bar.y <= layout.progress.y && bar.bottom == 270)
        assertTrue(layout.progress.bottom <= layout.controls.y)
        assertTrue(layout.controls.bottom <= 270)
        assertTrue(layout.fullscreenToggle.right <= 480 && layout.fullscreenToggle.bottom < bar.y)
        assertTrue(layout.controlButton(HistoryReplayLayout.CONTROL_BUTTONS - 1).right < layout.speedSelector().x)
    }

    /** 一般版面沒有底部控制區，全螢幕按鈕在場景右上角。 */
    @Test
    fun `regular layout puts the fullscreen toggle inside the scene`() {
        val layout = HistoryReplayLayout.measure(width = 480, height = 270, labelWidth = LABEL_WIDTH, speedSelectorWidth = SPEED_WIDTH)

        assertNull(layout.controlBar)
        assertTrue(layout.fullscreenToggle.x >= layout.scene.x && layout.fullscreenToggle.right <= layout.scene.right)
        assertTrue(layout.fullscreenToggle.y >= layout.scene.y && layout.fullscreenToggle.bottom <= layout.scene.bottom)
    }

    /** 測試用的尺寸。 */
    private companion object {
        /** 測試用的標籤寬度。 */
        const val LABEL_WIDTH = 24

        /** 測試用的速度選擇器寬度。 */
        const val SPEED_WIDTH = 64
    }
}
