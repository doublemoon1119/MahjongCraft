package com.doublemoon1119.mahjongcraft.platform.minecraft.ruler

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證牌尺顏色與物品註冊路徑的對應。 */
class MahjongRulerColorTest {
    /** 無色加上原版 16 種染料色，每種顏色各有不同的物品路徑。 */
    @Test
    fun `every vanilla dye has its own ruler next to the clear one`() {
        assertEquals(VANILLA_DYES, MahjongRulerColor.entries.mapNotNull { it.dyeName }.toSet())
        assertEquals(MahjongRulerColor.entries.size, MahjongRulerColor.entries.map { it.itemPath }.toSet().size)
    }

    /** 無色牌尺使用基本路徑，染色牌尺在前面加上染料顏色。 */
    @Test
    fun `item paths follow the vanilla coloured item naming`() {
        assertEquals("mahjong_ruler", MahjongRulerColor.CLEAR.itemPath)
        assertEquals("light_blue_mahjong_ruler", MahjongRulerColor.LIGHT_BLUE.itemPath)
    }

    /** 列舉名稱可以還原顏色，不存在的名稱還原成無色。 */
    @Test
    fun `names round trip and unknown names fall back to clear`() {
        MahjongRulerColor.entries.forEach { assertEquals(it, MahjongRulerColor.fromNameOrDefault(it.name)) }
        assertEquals(MahjongRulerColor.CLEAR, MahjongRulerColor.fromNameOrDefault("rainbow"))
    }

    private companion object {
        /** 原版的 16 種染料顏色名稱。 */
        val VANILLA_DYES: Set<String> = setOf(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
        )
    }
}
