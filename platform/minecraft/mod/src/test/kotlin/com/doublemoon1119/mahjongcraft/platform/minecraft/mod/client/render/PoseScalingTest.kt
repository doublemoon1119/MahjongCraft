package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertEquals

/** 只縮放位置的轉換在每個版本都保留原本的法線。 */
class PoseScalingTest {
    /** 各軸倍數不同時位置照倍數縮放，法線維持縮放前的轉換。 */
    @Test
    fun `stretching keeps the previous normal transform`() {
        val matrices = PoseStack()
        matrices.mulPose(Quaternionf().rotateY(0.5f))
        val before = Matrix3f(matrices.last().normal())

        matrices.scaleKeepingNormals(0.2f, 0.4f, 0.3f)

        assertEquals(before, matrices.last().normal())
        val position = matrices.last().pose().transformDirection(Vector3f(1f, 1f, 1f))
        assertEquals(0.4f, position.y, 1e-5f)
        assertEquals(1f, matrices.last().normal().transform(Vector3f(0f, 1f, 0f)).length(), 1e-5f)
    }

    /** 翻轉某個座標軸只影響位置，法線仍是單位長度且方向不變。 */
    @Test
    fun `flipping an axis leaves normals untouched`() {
        val matrices = PoseStack()

        matrices.scaleKeepingNormals(24f, -24f, 24f)

        assertEquals(Matrix3f(), matrices.last().normal())
        assertEquals(-24f, matrices.last().pose().transformDirection(Vector3f(0f, 1f, 0f)).y, 1e-4f)
    }
}
