package com.doublemoon1119.mahjongcraft.platform.fabric.client.character

import net.minecraft.client.util.math.MatrixStack
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 以實際矩陣保護邏輯驗證 GUI 與離屏投影，無須建立 OpenGL 視窗。 */
class CharacterPreviewProjectionTest {
    /** 共用保護在 GUI 內保留深度位移，不能把整個模型裁掉。 */
    @Test
    fun `shared scope retains GUI depth inside the clipping planes`() {
        val matrices = MatrixStack()
        matrices.translate(0.0, 0.0, -11000.0)
        val projection = Matrix4f().setOrtho(0f, 320f, 180f, 0f, 1000f, 21000f)
        CharacterPreviewRenderState.ModelViewScope(matrices).use {
            val point = projection.mul(matrices.peek().positionMatrix).transformProject(Vector3f(160f, 90f, 50f))
            assertTrue(point.z in -1f..1f, "The production scope must retain GUI depth translation")
        }
    }

    /** 離屏圖片可自行重設原點，但結束後仍回到原本的 GUI 座標系。 */
    @Test
    fun `offscreen reset restores the caller model view`() {
        val matrices = MatrixStack()
        matrices.translate(0.0, 0.0, -11000.0)
        val original = Matrix4f(matrices.peek().positionMatrix)
        CharacterPreviewRenderState.ModelViewScope(matrices).use {
            matrices.loadIdentity()
            val projection = Matrix4f().setOrtho(0f, 128f, 128f, 0f, -1000f, 1000f)
            val point = projection.mul(matrices.peek().positionMatrix).transformProject(Vector3f(64f, 64f, 50f))
            assertTrue(point.z in -1f..1f, "Portrait capture must use its own projection")
        }
        assertEquals(original, matrices.peek().positionMatrix)
    }

    /** 提供者繪製失敗也不能把新的位移留給後面的介面。 */
    @Test
    fun `failing preview restores the caller model view`() {
        val matrices = MatrixStack()
        matrices.translate(0.0, 0.0, -11000.0)
        val original = Matrix4f(matrices.peek().positionMatrix)
        assertFailsWith<IllegalStateException> {
            CharacterPreviewRenderState.ModelViewScope(matrices).use {
                matrices.loadIdentity()
                error("Simulated renderer failure")
            }
        }
        assertEquals(original, matrices.peek().positionMatrix)
    }
}
