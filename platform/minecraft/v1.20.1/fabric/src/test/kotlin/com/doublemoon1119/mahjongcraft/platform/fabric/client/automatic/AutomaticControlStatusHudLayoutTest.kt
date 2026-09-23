package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 驗證常駐 HUD 在 GUI scaled 畫面內的尺寸與續欄行為。 */
class AutomaticControlStatusHudLayoutTest {
    @Test
    fun `normal rows retain configured relative position`() {
        val layout = assertNotNull(automaticControlStatusHudLayout(listOf(45, 72, 60), 10, 320, 240, 0.03, 0.22, { 30 }))

        assertEquals(1f, layout.scale)
        assertEquals(0, layout.hiddenCount)
        assertEquals(listOf(0, 1, 2), layout.placements.map { it.rowIndex })
        assertEquals(72 + 12, layout.rawWidth)
        assertEquals(12 + 2 * 12 + 10, layout.rawHeight)
        assertEquals(((320 - layout.bounds.width) * 0.03).roundToInt(), layout.bounds.left)
        assertEquals(((240 - layout.bounds.height) * 0.22).roundToInt(), layout.bounds.top)
    }

    @Test
    fun `long control list uses second column and summary without leaving screen`() {
        val layout = assertNotNull(automaticControlStatusHudLayout(List(40) { 90 }, 10, 240, 80, 1.0, 1.0, { 35 }))

        assertTrue(layout.hiddenCount > 0)
        assertEquals(null, layout.placements.last().rowIndex)
        assertTrue(layout.placements.map { it.x }.distinct().size == 2)
        assertTrue(layout.bounds.left >= 0)
        assertTrue(layout.bounds.top >= 0)
        assertTrue(layout.bounds.right <= 240)
        assertTrue(layout.bounds.bottom <= 80)
    }

    @Test
    fun `narrow screen prefers one readable column to tiny two column text`() {
        val layout = assertNotNull(automaticControlStatusHudLayout(List(20) { 90 }, 10, 110, 80, 0.0, 0.0, { 35 }))

        assertEquals(1, layout.placements.map { it.x }.distinct().size)
        assertTrue(layout.hiddenCount > 0)
        assertEquals(1f, layout.scale)
    }

    @Test
    fun `empty or unavailable screen has no layout`() {
        assertEquals(null, automaticControlStatusHudLayout(emptyList(), 10, 320, 240, 0.0, 0.0, { 0 }))
        assertEquals(null, automaticControlStatusHudLayout(listOf(20), 10, 0, 240, 0.0, 0.0, { 0 }))
    }
}
