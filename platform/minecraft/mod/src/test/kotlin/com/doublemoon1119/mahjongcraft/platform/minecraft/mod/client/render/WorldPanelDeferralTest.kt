package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class WorldPanelDeferralTest {
    /** 模型檢視矩陣是單位矩陣時，面板的座標轉換保持原樣。 */
    @Test
    fun `keeps the pose when the model view is identity`() {
        val pose = Matrix4f().translation(1f, 2f, 3f).rotateY(QUARTER_TURN)
        val normal = Matrix3f().rotateY(QUARTER_TURN)

        val (position, normalResult) = WorldPanelDeferral.cameraSpaceTransform(Matrix4f(), pose, normal)

        assertEquals(pose, position)
        assertEquals(normal, normalResult)
    }

    /** 鏡頭的轉向放在模型檢視矩陣時，面板的位置與朝向一起跟著轉。 */
    @Test
    fun `applies the camera rotation held by the model view`() {
        val modelView = Matrix4f().rotationY(QUARTER_TURN)
        val pose = Matrix4f().translation(0f, 0f, -5f)

        val (position, normal) = WorldPanelDeferral.cameraSpaceTransform(modelView, pose, Matrix3f())

        val origin = position.transformPosition(Vector3f())
        assertEquals(-5f, origin.x, TOLERANCE)
        assertEquals(0f, origin.y, TOLERANCE)
        assertEquals(0f, origin.z, TOLERANCE)
        val facing = normal.transform(Vector3f(0f, 0f, 1f))
        assertEquals(1f, facing.x, TOLERANCE)
        assertEquals(0f, facing.z, TOLERANCE)
    }

    /** 計算不改動傳入的矩陣。 */
    @Test
    fun `leaves the inputs unchanged`() {
        val modelView = Matrix4f().rotationY(QUARTER_TURN)
        val pose = Matrix4f().translation(1f, 0f, 0f)
        val normal = Matrix3f()

        WorldPanelDeferral.cameraSpaceTransform(modelView, pose, normal)

        assertEquals(Matrix4f().rotationY(QUARTER_TURN), modelView)
        assertEquals(Matrix4f().translation(1f, 0f, 0f), pose)
        assertEquals(Matrix3f(), normal)
    }

    private companion object {
        /** 四分之一圈。 */
        const val QUARTER_TURN: Float = (PI / 2).toFloat()

        /** 浮點數比對的容許誤差。 */
        const val TOLERANCE: Float = 1e-5f
    }
}
