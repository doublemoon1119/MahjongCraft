package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證宣傳截圖判斷桌面照不照得到陽光的方式。 */
class ShowcaseSunlightTest {
    /** 正午的太陽在正上方，日落時在西邊的地平線上。 */
    @Test
    fun `the sun is overhead at noon and in the west at sunset`() {
        val (noonX, noonY, _) = ShowcaseSunlight.sunDirection(0f)
        val (sunsetX, sunsetY, _) = ShowcaseSunlight.sunDirection(0.25f)

        assertEquals(0.0, noonX, 1e-9)
        assertEquals(1.0, noonY, 1e-9)
        assertEquals(-1.0, sunsetX, 1e-9)
        assertEquals(0.0, sunsetY, 1e-9)
    }

    /** 四周空曠時整張桌面都照得到陽光。 */
    @Test
    fun `an open table is fully lit`() {
        assertEquals(1.0, ShowcaseSunlight.litFraction(0, 64, 0, LOW_WEST_SUN) { _, _, _ -> false })
    }

    /** 西邊一整面高牆擋住傍晚的太陽時，整張桌面都在陰影裡。 */
    @Test
    fun `a wall towards the sun shades the whole table`() {
        assertEquals(0.0, ShowcaseSunlight.litFraction(0, 64, 0, LOW_WEST_SUN) { x, _, _ -> x == -10 })
    }

    /** 只擋住桌面南半邊的樹冠，讓一部分桌面在陰影裡。 */
    @Test
    fun `a canopy over part of the sun's path shades only part of the table`() {
        val lit = ShowcaseSunlight.litFraction(0, 64, 0, LOW_WEST_SUN) { x, y, z -> x == -10 && z >= 1 && y in 60..80 }

        assertTrue(lit > 0.0 && lit < 1.0, "lit $lit")
    }

    private companion object {
        /** 傍晚、從西邊低處照過來的陽光。 */
        val LOW_WEST_SUN = ShowcaseSunlight.sunDirection(0.215f)
    }
}
