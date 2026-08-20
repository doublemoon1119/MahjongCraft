package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證麻將牌姿態的固定循環及持久化 fallback。 */
class MahjongTilePoseTest {
    /** 驗證直立、正面朝上與面朝下依固定順序循環。 */
    @Test
    fun `poses cycle through standing face up and face down`() {
        assertEquals(MahjongTilePose.FACE_UP, MahjongTilePose.STANDING.next())
        assertEquals(MahjongTilePose.FACE_DOWN, MahjongTilePose.FACE_UP.next())
        assertEquals(MahjongTilePose.STANDING, MahjongTilePose.FACE_DOWN.next())
    }

    /** 驗證非法 ordinal 與名稱使用直立姿態。 */
    @Test
    fun `invalid persisted poses fall back to standing`() {
        assertEquals(MahjongTilePose.STANDING, MahjongTilePose.fromOrdinalOrDefault(-1))
        assertEquals(MahjongTilePose.STANDING, MahjongTilePose.fromNameOrDefault("SIDEWAYS"))
        assertEquals(MahjongTilePose.FACE_DOWN, MahjongTilePose.fromNameOrDefault("FACE_DOWN"))
    }

    /** 驗證各姿態對應的 renderer 旋轉角度。 */
    @Test
    fun `poses map to their renderer rotation degrees`() {
        assertEquals(0.0f, MahjongTilePose.STANDING.rotationDegrees)
        assertEquals(90.0f, MahjongTilePose.FACE_UP.rotationDegrees)
        assertEquals(-90.0f, MahjongTilePose.FACE_DOWN.rotationDegrees)
    }
}
