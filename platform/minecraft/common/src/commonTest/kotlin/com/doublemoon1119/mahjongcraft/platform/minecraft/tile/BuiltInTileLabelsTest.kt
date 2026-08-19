package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 驗證內建牌面角落標籤的顏色與文字規則（使用者指定的赤牌／花牌規則）。 */
class BuiltInTileLabelsTest {
    private val registry = TileLabelRegistryImpl().apply { registerBuiltInTileLabels() }

    /** 一般數牌只有右上角紅色數字，左上角不顯示。 */
    @Test
    fun `normal numeric tile shows red digit at top right only`() {
        val label = registry.find("p9")
        assertNull(label?.topLeft)
        assertEquals(TileLabelText("9", TileLabelColor.RED), label?.topRight)
    }

    /** 赤五（牌面本身印刷成紅色）右上角文字改用黑色維持對比。 */
    @Test
    fun `red five uses black text for contrast against the red tile face`() {
        val label = registry.find("m5_red")
        assertNull(label?.topLeft)
        assertEquals(TileLabelText("5", TileLabelColor.BLACK), label?.topRight)
    }

    /** 紅中牌面本身印刷成紅色，跟赤五歸為同一種黑字規則。 */
    @Test
    fun `red dragon uses black text like the red fives`() {
        val label = registry.find("red_dragon")
        assertEquals(TileLabelText("R", TileLabelColor.BLACK), label?.topRight)
    }

    /** 其餘字牌（風牌、發、白）維持一般紅色文字規則。 */
    @Test
    fun `other honor tiles use the normal red text rule`() {
        assertEquals(TileLabelText("W", TileLabelColor.RED), registry.find("west")?.topRight)
        assertEquals(TileLabelText("G", TileLabelColor.RED), registry.find("green_dragon")?.topRight)
        assertEquals(TileLabelText("Wh", TileLabelColor.RED), registry.find("white_dragon")?.topRight)
    }

    /** 四季花牌：右上紅色中文字、左上黑色數字，依春夏秋冬排序 1～4。 */
    @Test
    fun `seasonal flowers pair red chinese character with black order number`() {
        assertEquals(
            TileLabel(topLeft = TileLabelText("1", TileLabelColor.BLACK), topRight = TileLabelText("春", TileLabelColor.RED)),
            registry.find("flower_spring"),
        )
        assertEquals(
            TileLabel(topLeft = TileLabelText("4", TileLabelColor.BLACK), topRight = TileLabelText("冬", TileLabelColor.RED)),
            registry.find("flower_winter"),
        )
    }

    /** 四君子花牌：右上紅色數字、左上黑色中文字，依梅蘭菊竹排序 1～4。 */
    @Test
    fun `plant flowers pair black chinese character with red order number in plum orchid chrysanthemum bamboo order`() {
        assertEquals(
            TileLabel(topLeft = TileLabelText("梅", TileLabelColor.BLACK), topRight = TileLabelText("1", TileLabelColor.RED)),
            registry.find("flower_plum"),
        )
        assertEquals(
            TileLabel(topLeft = TileLabelText("蘭", TileLabelColor.BLACK), topRight = TileLabelText("2", TileLabelColor.RED)),
            registry.find("flower_orchid"),
        )
        assertEquals(
            TileLabel(topLeft = TileLabelText("菊", TileLabelColor.BLACK), topRight = TileLabelText("3", TileLabelColor.RED)),
            registry.find("flower_chrysanthemum"),
        )
        assertEquals(
            TileLabel(topLeft = TileLabelText("竹", TileLabelColor.BLACK), topRight = TileLabelText("4", TileLabelColor.RED)),
            registry.find("flower_bamboo"),
        )
    }

    /** 未知佔位牌不註冊標籤。 */
    @Test
    fun `unknown placeholder tile has no label`() {
        assertNull(registry.find(UNKNOWN_TILE_ASSET_KEY))
    }
}
