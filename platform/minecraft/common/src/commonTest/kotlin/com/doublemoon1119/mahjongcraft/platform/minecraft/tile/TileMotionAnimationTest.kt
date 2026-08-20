package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證牌張運動動畫的位移、姿態旋轉內插與結束契約。 */
class TileMotionAnimationTest {
    /** 共用 20 ticks、有拋物線高度與姿態旋轉變化的規格，讓所有效果都測得到。 */
    private val animation = TileMotionAnimation(
        TileMotionAnimationSpec(
            durationTicks = 20,
            arcHeight = 0.4,
            startPoseRotationDegrees = -90.0f,
            endPoseRotationDegrees = 0.0f,
        ),
    )

    /** 動畫剛開始時應完全在起點，姿態旋轉角等於起始值。 */
    @Test
    fun `animation starts at the configured start offset and pose`() {
        val startOffset = DiceAnimationVector(0.0, 1.5, 0.0)
        val frame = animation.frame(elapsedTicks = 0.0, startOffset = startOffset)

        assertVectorEquals(startOffset, frame.offset)
        assertEquals(-90.0f, frame.poseRotationDegrees)
        assertEquals(0.0, frame.progress)
        assertFalse(frame.completed)
    }

    /** 動畫播完（或超過總長度）應收斂為零位移、終點姿態旋轉角，並標記為已完成。 */
    @Test
    fun `animation completes at zero offset and end pose`() {
        val startOffset = DiceAnimationVector(0.0, 1.5, 0.0)
        val atEnd = animation.frame(elapsedTicks = 20.0, startOffset = startOffset)
        val pastEnd = animation.frame(elapsedTicks = 999.0, startOffset = startOffset)

        assertVectorEquals(DiceAnimationVector.ZERO, atEnd.offset)
        assertEquals(0.0f, atEnd.poseRotationDegrees)
        assertTrue(atEnd.completed)
        assertVectorEquals(DiceAnimationVector.ZERO, pastEnd.offset)
        assertTrue(pastEnd.completed)
    }

    /** 姿態旋轉角應隨進度線性內插。 */
    @Test
    fun `pose rotation interpolates linearly with progress`() {
        val frame = animation.frame(elapsedTicks = 10.0, startOffset = DiceAnimationVector.ZERO)

        assertEquals(-45.0f, frame.poseRotationDegrees, ABSOLUTE_TOLERANCE_FLOAT)
        assertEquals(0.5, frame.progress)
    }

    /**
     * 拋物線高度應在動畫中點附近達到最大、起訖兩端為零——用起訖點同高（`startOffset.y = 0.0`）的位移
     * 隔離拋物線本身的貢獻：沒有拋物線時中點理應也是 `0.0`，額外多出來的高度就是純粹的 [TileMotionAnimationSpec.arcHeight] 貢獻。
     */
    @Test
    fun `arc height peaks near the midpoint and vanishes at both ends`() {
        val startOffset = DiceAnimationVector(0.0, 0.0, 0.0)
        val start = animation.frame(elapsedTicks = 0.0, startOffset = startOffset)
        val midpoint = animation.frame(elapsedTicks = 10.0, startOffset = startOffset)
        val end = animation.frame(elapsedTicks = 20.0, startOffset = startOffset)

        assertEquals(0.0, start.offset.y, ABSOLUTE_TOLERANCE_DOUBLE)
        assertEquals(0.4, midpoint.offset.y, ABSOLUTE_TOLERANCE_DOUBLE)
        assertEquals(0.0, end.offset.y, ABSOLUTE_TOLERANCE_DOUBLE)
        assertTrue(midpoint.offset.y > start.offset.y)
    }

    /**
     * `arcHeight = 0` 時位移不套用拋物線，但仍套用 ease-out（1 減去「1 減 progress」的平方）——先快後慢
     * 的觀感，見 [TileMotionAnimation.frame] 的內部註解；時間過半（`progress = 0.5`）時實際位移比純
     * 線性內插（0.5）更接近終點（`0.25`），因為前半段已經跑掉大半段距離，後半段才開始減速。
     */
    @Test
    fun `zero arc height eases out without an arc`() {
        val straightFallAnimation = TileMotionAnimation(
            TileMotionAnimationSpec(durationTicks = 10, arcHeight = 0.0, startPoseRotationDegrees = -90.0f, endPoseRotationDegrees = -90.0f),
        )
        val startOffset = DiceAnimationVector(0.0, 1.0, 0.0)
        val midpoint = straightFallAnimation.frame(elapsedTicks = 5.0, startOffset = startOffset)

        assertEquals(0.25, midpoint.offset.y, ABSOLUTE_TOLERANCE_DOUBLE)
    }

    /** 起訖姿態旋轉角相同時，全程應保持不變，符合牌牆生成／發牌只有位置在動的設計。 */
    @Test
    fun `pose rotation stays constant when start and end are equal`() {
        val sameRotationAnimation = TileMotionAnimation(
            TileMotionAnimationSpec(durationTicks = 10, arcHeight = 0.0, startPoseRotationDegrees = -90.0f, endPoseRotationDegrees = -90.0f),
        )
        val midpoint = sameRotationAnimation.frame(elapsedTicks = 5.0, startOffset = DiceAnimationVector.ZERO)

        assertEquals(-90.0f, midpoint.poseRotationDegrees)
    }

    private fun assertVectorEquals(expected: DiceAnimationVector, actual: DiceAnimationVector) {
        assertEquals(expected.x, actual.x, ABSOLUTE_TOLERANCE_DOUBLE)
        assertEquals(expected.y, actual.y, ABSOLUTE_TOLERANCE_DOUBLE)
        assertEquals(expected.z, actual.z, ABSOLUTE_TOLERANCE_DOUBLE)
    }

    private companion object {
        const val ABSOLUTE_TOLERANCE_DOUBLE: Double = 1e-9
        const val ABSOLUTE_TOLERANCE_FLOAT: Float = 1e-4f
    }
}
