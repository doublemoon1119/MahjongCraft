package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證牌譜回放畫面在寬窄視窗的版面不重疊、不超出視窗。 */
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
        assertTrue(layout.controls.bottom <= layout.footerTop)
    }

    /** 測試用的尺寸。 */
    private companion object {
        /** 測試用的標籤寬度。 */
        const val LABEL_WIDTH = 24

        /** 測試用的速度選擇器寬度。 */
        const val SPEED_WIDTH = 64
    }
}
