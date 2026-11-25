package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.client.Camera
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertEquals

class CameraFacingTest {
    /** 面向鏡頭的轉向把平面的前方與上方對齊鏡頭的視線與上方，不受原版鏡頭轉向的寫法影響。 */
    @Test
    fun `aligns the panel with the camera view`() {
        listOf(0f to 0f, 30f to -20f, 135f to 45f, -90f to 10f).forEach { (yaw, pitch) ->
            val camera = TestCamera().apply { look(yaw, pitch) }
            val facing = cameraFacingRotation(camera.rotation())

            assertClose(Vector3f(camera.lookVector), facing.transform(Vector3f(0f, 0f, 1f)), "look at $yaw, $pitch")
            assertClose(Vector3f(camera.upVector), facing.transform(Vector3f(0f, 1f, 0f)), "up at $yaw, $pitch")
        }
    }

    /** 由面向鏡頭轉向換成原版鏡頭轉向，再換回來時不變。 */
    @Test
    fun `converts a facing rotation back and forth`() {
        val rotation = Quaternionf().rotationYXZ(0.7f, -0.3f, 0f)

        val facing = cameraFacingRotation(cameraOrientationFacing(rotation))

        assertClose(rotation.transform(Vector3f(0f, 0f, 1f)), facing.transform(Vector3f(0f, 0f, 1f)), "look")
        assertClose(rotation.transform(Vector3f(0f, 1f, 0f)), facing.transform(Vector3f(0f, 1f, 0f)), "up")
    }

    /** 可以直接指定視角的鏡頭。 */
    private class TestCamera : Camera() {
        /**
         * 轉向指定的視角。
         *
         * @param yaw 水平角度。
         * @param pitch 俯仰角度。
         */
        fun look(yaw: Float, pitch: Float) {
            setRotation(yaw, pitch)
        }
    }

    /**
     * 比對兩個向量。
     *
     * @param expected 預期的向量。
     * @param actual 實際的向量。
     * @param message 失敗時的說明。
     */
    private fun assertClose(expected: Vector3f, actual: Vector3f, message: String) {
        assertEquals(expected.x, actual.x, TOLERANCE, message)
        assertEquals(expected.y, actual.y, TOLERANCE, message)
        assertEquals(expected.z, actual.z, TOLERANCE, message)
    }

    private companion object {
        /** 浮點數比對的容許誤差。 */
        const val TOLERANCE: Float = 1e-5f
    }
}
