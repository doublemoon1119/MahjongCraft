package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證鳴牌提示的牌組排列：一列等距的直立牌，置中於原點。 */
class TileGroupPreviewLayoutTest {
    /** 測試牌面的寬度。 */
    private val tileWidth = 20f

    /** 測試牌面的高度。 */
    private val tileHeight = 28f

    /** 測試牌面之間的間距。 */
    private val gap = 2f

    /**
     * 以測試尺寸建立預覽牌列。
     *
     * @param entries 依顯示順序排列的牌組項目。
     * @return 使用固定測試尺寸計算的牌組版面。
     */
    private fun layoutOf(vararg entries: TileGroupPreviewEntry) = TileGroupPreviewLayoutCalculator.calculate(entries.toList(), tileWidth, tileHeight, gap)

    /** 牌依傳入順序等距排開，整列水平置中於原點，垂直維持同一列。 */
    @Test
    fun `tiles are evenly spaced and centered on zero`() {
        val layout = layoutOf(
            TileGroupPreviewEntry("m1"),
            TileGroupPreviewEntry("m2"),
            TileGroupPreviewEntry("m3"),
        )

        assertEquals(listOf("m1", "m2", "m3"), layout.placements.map { it.assetKey })
        assertEquals(tileWidth * 3 + gap * 2, layout.contentWidth)
        assertEquals(tileHeight, layout.contentHeight)
        assertTrue(abs(layout.placements[1].centerX) < 0.001f, "the middle tile sits on the origin")
        assertEquals(tileWidth + gap, layout.placements[1].centerX - layout.placements[0].centerX)
        assertEquals(tileWidth + gap, layout.placements[2].centerX - layout.placements[1].centerX)
        assertTrue(layout.placements.all { it.centerY == 0f }, "every tile stays on the same row")
    }

    /** 鳴取的那張只是被標記，位置與其他牌一致。 */
    @Test
    fun `the claimed tile keeps its place in the row`() {
        val marked = layoutOf(
            TileGroupPreviewEntry("m1"),
            TileGroupPreviewEntry("m2", claimed = true),
            TileGroupPreviewEntry("m3"),
        )
        val unmarked = layoutOf(
            TileGroupPreviewEntry("m1"),
            TileGroupPreviewEntry("m2"),
            TileGroupPreviewEntry("m3"),
        )

        assertEquals(listOf(false, true, false), marked.placements.map { it.claimed })
        assertEquals(unmarked.placements.map { it.centerX }, marked.placements.map { it.centerX })
    }

    /** 沒有牌時沒有內容。 */
    @Test
    fun `an empty group has no content`() {
        val layout = layoutOf()

        assertEquals(0f, layout.contentWidth)
        assertEquals(0f, layout.contentHeight)
        assertTrue(layout.placements.isEmpty())
    }
}
