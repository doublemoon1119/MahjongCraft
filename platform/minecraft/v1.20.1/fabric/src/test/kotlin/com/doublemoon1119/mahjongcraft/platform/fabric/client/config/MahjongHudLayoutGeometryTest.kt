package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證 HUD 比例座標與完整畫面邊界限制。 */
class MahjongHudLayoutGeometryTest {
    /** 零、中央與一應分別映射至合法移動範圍的兩端與中央。 */
    @Test
    fun `coordinate maps ratios across the legal travel range`() {
        assertEquals(0, hudCoordinate(0.0, 320, 80))
        assertEquals(120, hudCoordinate(0.5, 320, 80))
        assertEquals(240, hudCoordinate(1.0, 320, 80))
    }

    /** 超出範圍的座標與比例必須被限制，確保 HUD 完整留在畫面內。 */
    @Test
    fun `coordinates and ratios are clamped to screen bounds`() {
        assertEquals(0, hudCoordinate(-1.0, 320, 80))
        assertEquals(240, hudCoordinate(2.0, 320, 80))
        assertEquals(0.0, hudRatio(-20, 320, 80))
        assertEquals(1.0, hudRatio(300, 320, 80))
    }

    /** 比例與座標往返只允許整數像素造成的微小誤差。 */
    @Test
    fun `ratio round trip remains stable within one pixel`() {
        val coordinate = hudCoordinate(0.37, 427, 113)
        val restored = hudRatio(coordinate, 427, 113)

        assertTrue(kotlin.math.abs(restored - 0.37) <= 1.0 / (427 - 113))
    }
}
