package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 驗證確定性骰子翻滾動畫的位移、彈跳、旋轉與結束契約。 */
class DiceRollAnimationTest {
    /** 共用 100 ticks 規格，使標準化時間可直接換算。 */
    private val animation = DiceRollAnimation(
        DiceRollAnimationSpec(
            durationTicks = 100,
            startOffsetX = -0.8,
            startOffsetY = 0.0,
            startOffsetZ = 0.2,
            tossHeight = 0.5,
            firstBounceHeight = 0.12,
            secondBounceHeight = 0.04,
        ),
    )

    /** 驗證動畫從指定視覺起點開始。 */
    @Test
    fun `animation starts at configured offset`() {
        val frame = animation.frame(seed = 42L, elapsedTicks = 0.0)

        assertVectorEquals(DiceAnimationVector(-0.8, 0.0, 0.2), frame.offset)
        assertEquals(0.0, frame.progress)
        assertFalse(frame.completed)
    }

    /** 驗證每次投擲可使用同步的玩家手部相對位移覆寫預設起點。 */
    @Test
    fun `animation accepts a synchronized per-roll start offset`() {
        val startOffset = DiceAnimationVector(1.25, 0.75, -0.5)
        val frame = animation.frame(seed = 42L, elapsedTicks = 0.0, startOffset = startOffset)

        assertVectorEquals(startOffset, frame.offset)
    }

    /** 驗證拋出中段同時具有水平移動、側向弧線與高度。 */
    @Test
    fun `flight midpoint is elevated and moving toward landing point`() {
        val frame = animation.frame(seed = 42L, elapsedTicks = 27.5)

        assertTrue(frame.offset.x in -0.8..0.0)
        assertTrue(frame.offset.y > 0.49)
        assertNotEquals(0.1, frame.offset.z)
        assertNotEquals(DiceAnimationQuaternion.IDENTITY, frame.rotation)
    }

    /** 驗證兩次彈跳的峰值依序降低。 */
    @Test
    fun `second bounce is lower than first bounce`() {
        val firstBouncePeak = animation.frame(seed = 42L, elapsedTicks = 66.5)
        val secondBouncePeak = animation.frame(seed = 42L, elapsedTicks = 85.5)

        assertEquals(0.12, firstBouncePeak.offset.y, TOLERANCE)
        assertEquals(0.04, secondBouncePeak.offset.y, TOLERANCE)
        assertTrue(secondBouncePeak.offset.y < firstBouncePeak.offset.y)
    }

    /** 驗證動畫結束時位移與額外旋轉完全歸零。 */
    @Test
    fun `completed animation returns stable final frame`() {
        val frame = animation.frame(seed = 42L, elapsedTicks = 100.0)

        assertVectorEquals(DiceAnimationVector.ZERO, frame.offset)
        assertQuaternionEquals(DiceAnimationQuaternion.IDENTITY, frame.rotation)
        assertEquals(1.0, frame.progress)
        assertTrue(frame.completed)
    }

    /** 驗證超出總長度的時間仍維持相同結束 frame。 */
    @Test
    fun `elapsed time is clamped after completion`() {
        assertEquals(
            animation.frame(seed = 42L, elapsedTicks = 100.0),
            animation.frame(seed = 42L, elapsedTicks = 500.0),
        )
    }

    /** 驗證相同 seed 與時間能跨次數重建完全相同的 frame。 */
    @Test
    fun `same seed reproduces identical frame`() {
        assertEquals(
            animation.frame(seed = 987654321L, elapsedTicks = 41.25),
            animation.frame(seed = 987654321L, elapsedTicks = 41.25),
        )
    }

    /** 驗證不同 seed 產生不同的側向弧線及翻滾軸。 */
    @Test
    fun `different seeds vary lateral path and rotation`() {
        val first = animation.frame(seed = 1L, elapsedTicks = 27.5)
        val second = animation.frame(seed = 2L, elapsedTicks = 27.5)

        assertNotEquals(first.offset.z, second.offset.z)
        assertNotEquals(first.rotation, second.rotation)
    }

    /** 驗證收斂階段在結束前已停止彈跳並逐步接近 identity。 */
    @Test
    fun `settling phase stops movement before rotation completes`() {
        val earlySettling = animation.frame(seed = 42L, elapsedTicks = 90.0)
        val settling = animation.frame(seed = 42L, elapsedTicks = 96.0)

        assertVectorEquals(DiceAnimationVector.ZERO, settling.offset)
        assertFalse(settling.completed)
        assertTrue(
            quaternionDistance(settling.rotation, DiceAnimationQuaternion.IDENTITY) <
                quaternionDistance(earlySettling.rotation, DiceAnimationQuaternion.IDENTITY),
        )
    }

    /** 驗證所有輸出 quaternion 均保持單位長度。 */
    @Test
    fun `animation rotations remain normalized`() {
        listOf(0.0, 20.0, 55.0, 70.0, 85.0, 96.0, 100.0).forEach { elapsedTicks ->
            val rotation = animation.frame(seed = 123L, elapsedTicks = elapsedTicks).rotation
            val squaredLength = rotation.x * rotation.x + rotation.y * rotation.y +
                rotation.z * rotation.z + rotation.w * rotation.w

            assertEquals(1.0, squaredLength, TOLERANCE)
        }
    }

    /** 比對三維向量各分量。 */
    private fun assertVectorEquals(expected: DiceAnimationVector, actual: DiceAnimationVector) {
        assertEquals(expected.x, actual.x, TOLERANCE)
        assertEquals(expected.y, actual.y, TOLERANCE)
        assertEquals(expected.z, actual.z, TOLERANCE)
    }

    /** 比對 quaternion 各分量。 */
    private fun assertQuaternionEquals(expected: DiceAnimationQuaternion, actual: DiceAnimationQuaternion) {
        assertEquals(expected.x, actual.x, TOLERANCE)
        assertEquals(expected.y, actual.y, TOLERANCE)
        assertEquals(expected.z, actual.z, TOLERANCE)
        assertEquals(expected.w, actual.w, TOLERANCE)
    }

    /** 計算 quaternion 分量差距，供收斂測試使用。 */
    private fun quaternionDistance(first: DiceAnimationQuaternion, second: DiceAnimationQuaternion): Double = abs(
        first.x - second.x,
    ) + abs(first.y - second.y) + abs(first.z - second.z) + abs(first.w - second.w)

    private companion object {
        /** 浮點計算允許誤差。 */
        const val TOLERANCE = 1.0e-9
    }
}
