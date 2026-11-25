package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertEquals

class PoseMatricesTest {
    /** 位置乘上指定矩陣，法向量轉換照這個版本的原版維持不變，畫面中人物預覽的光照才與原版相同。 */
    @Test
    fun `multiplies the position and leaves the normals`() {
        val matrices = PoseStack()

        matrices.mulPose(Matrix4f().scaling(30f, 30f, -30f))

        assertEquals(30f, matrices.last().pose().transformPosition(Vector3f(1f, 0f, 0f)).x, TOLERANCE)
        assertEquals(Matrix3f(), matrices.last().normal())
    }

    private companion object {
        /** 浮點數比對的容許誤差。 */
        const val TOLERANCE: Float = 1e-5f
    }
}
