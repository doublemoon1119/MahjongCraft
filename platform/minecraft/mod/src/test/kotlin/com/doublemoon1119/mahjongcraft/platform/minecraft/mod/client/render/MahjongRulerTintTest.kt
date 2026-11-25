package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import com.doublemoon1119.mahjongcraft.platform.minecraft.ruler.MahjongRulerColor
import kotlin.test.Test
import kotlin.test.assertEquals

class MahjongRulerTintTest {
    /** 每種牌尺的物品染色都完全不透明，紅綠藍與牌尺的顏色相同。 */
    @Test
    fun `item tints are opaque with the ruler colour`() {
        MahjongRulerColor.entries.forEach { color ->
            val tint = MahjongRulerTint.itemTint(color)
            assertEquals(0xFF, tint ushr 24, color.name)
            assertEquals(MahjongRulerTint.rgb(color), tint and 0xFFFFFF, color.name)
        }
    }
}
