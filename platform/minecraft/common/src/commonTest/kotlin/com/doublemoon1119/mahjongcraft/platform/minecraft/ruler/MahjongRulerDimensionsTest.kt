package com.doublemoon1119.mahjongcraft.platform.minecraft.ruler

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證牌尺的擺法，以及依擺法與朝向佔用的範圍。 */
class MahjongRulerDimensionsTest {
    /** 面向南北時牌尺沿東西方向放，面向東西時沿南北方向；平放比豎放寬、比豎放矮。 */
    @Test
    fun `the long side lies across the placer's view`() {
        MahjongRulerPosture.entries.forEach { posture ->
            val width = MahjongRulerDimensions.width(posture)
            val facingSouth = MahjongRulerDimensions.footprint(0.0, posture)
            assertEquals(MahjongRulerDimensions.LENGTH / 2.0, facingSouth.halfX, EPSILON)
            assertEquals(width / 2.0, facingSouth.halfZ, EPSILON)

            val facingEast = MahjongRulerDimensions.footprint(QUARTER_TURN, posture)
            assertEquals(width / 2.0, facingEast.halfX, EPSILON)
            assertEquals(MahjongRulerDimensions.LENGTH / 2.0, facingEast.halfZ, EPSILON)
        }
        assertTrue(MahjongRulerDimensions.width(MahjongRulerPosture.FLAT) > MahjongRulerDimensions.width(MahjongRulerPosture.UPRIGHT))
        assertTrue(MahjongRulerDimensions.height(MahjongRulerPosture.FLAT) < MahjongRulerDimensions.height(MahjongRulerPosture.UPRIGHT))
    }

    /** 兩種擺法在任何朝向下，牌尺的四個角都在佔用範圍之內，且範圍貼齊最外側的角。 */
    @Test
    fun `the footprint tightly contains the ruler at any angle`() {
        MahjongRulerPosture.entries.forEach { posture ->
            (0 until FULL_TURN_STEPS).forEach { step ->
                val yaw = step * FULL_TURN / FULL_TURN_STEPS
                val footprint = MahjongRulerDimensions.footprint(yaw, posture)
                val radians = yaw * PI / (FULL_TURN / 2.0)
                val corners = listOf(-1.0, 1.0).flatMap { along -> listOf(-1.0, 1.0).map { across -> along to across } }.map { (along, across) ->
                    val alongOffset = along * MahjongRulerDimensions.LENGTH / 2.0
                    val acrossOffset = across * MahjongRulerDimensions.width(posture) / 2.0
                    (alongOffset * cos(radians) - acrossOffset * sin(radians)) to (alongOffset * sin(radians) + acrossOffset * cos(radians))
                }
                assertTrue(corners.all { (x, z) -> abs(x) <= footprint.halfX + EPSILON && abs(z) <= footprint.halfZ + EPSILON }, "$posture yaw $yaw")
                assertEquals(footprint.halfX, corners.maxOf { abs(it.first) }, EPSILON, "$posture yaw $yaw")
                assertEquals(footprint.halfZ, corners.maxOf { abs(it.second) }, EPSILON, "$posture yaw $yaw")
            }
        }
    }

    /** 擺法依序切換，名稱可以還原，不存在的名稱還原成平放。 */
    @Test
    fun `postures toggle and round trip by name`() {
        assertEquals(MahjongRulerPosture.UPRIGHT, MahjongRulerPosture.FLAT.next())
        assertEquals(MahjongRulerPosture.FLAT, MahjongRulerPosture.UPRIGHT.next())
        MahjongRulerPosture.entries.forEach { assertEquals(it, MahjongRulerPosture.fromNameOrDefault(it.name)) }
        assertEquals(MahjongRulerPosture.FLAT, MahjongRulerPosture.fromNameOrDefault("sideways"))
    }

    private companion object {
        /** 浮點比較容許誤差。 */
        const val EPSILON: Double = 1e-9

        /** 四分之一圈的角度。 */
        const val QUARTER_TURN: Double = 90.0

        /** 一整圈的角度。 */
        const val FULL_TURN: Double = 360.0

        /** 檢查一整圈時取的角度數。 */
        const val FULL_TURN_STEPS: Int = 72
    }
}
