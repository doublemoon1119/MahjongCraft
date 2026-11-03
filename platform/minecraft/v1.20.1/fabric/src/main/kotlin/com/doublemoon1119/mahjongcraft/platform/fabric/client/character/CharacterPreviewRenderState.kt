package com.doublemoon1119.mahjongcraft.platform.fabric.client.character

import com.doublemoon1119.mahjongcraft.platform.fabric.mixin.client.EntityRenderDispatcherAccessor
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.util.math.MatrixStack
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL30

/** 模型預覽的圖形狀態快照；外部 renderer 失敗也由 finally 還原。 */
internal class CharacterPreviewRenderState : AutoCloseable {
    /** 目前實體繪製器。 */
    private val dispatcher = MinecraftClient.getInstance().entityRenderDispatcher

    /** 原本相機旋轉。 */
    private val rotation = Quaternionf(dispatcher.rotation)

    /** 原本的陰影開關。 */
    private val shadows = (dispatcher as EntityRenderDispatcherAccessor).`mahjongcraft$getRenderShadows`()

    /** 原本的除錯框開關。 */
    private val hitboxes = (dispatcher as EntityRenderDispatcherAccessor).`mahjongcraft$getRenderHitboxes`()

    /** 原本的投影，不占用 RenderSystem 的單一備份欄位。 */
    private val projection = Matrix4f(RenderSystem.getProjectionMatrix())

    /** 原本的頂點排序。 */
    private val sorting = RenderSystem.getVertexSorting()

    /** 原本的繪製與讀取 framebuffer。 */
    private val drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)

    /** 原本的讀取 framebuffer。 */
    private val readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)

    /** 原本的 viewport。 */
    private val viewport = IntArray(4).also { GL11.glGetIntegerv(GL11.GL_VIEWPORT, it) }

    /** 原本的剪裁範圍，不讓畫面剪裁影響離屏圖片。 */
    private val scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)

    /** 原本的像素剪裁範圍。 */
    private val scissorBox = IntArray(4).also { GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, it) }

    /** 原本的深度、混色、剔除與深度寫入開關。 */
    private val depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)

    /** 原本是否混色。 */
    private val blend = GL11.glIsEnabled(GL11.GL_BLEND)

    /** 原本是否剔除背面。 */
    private val cull = GL11.glIsEnabled(GL11.GL_CULL_FACE)

    /** 原本是否寫入深度。 */
    private val depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)

    /** 原本的深度比較方式。 */
    private val depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC)

    /** 原本的混色來源及目標。 */
    private val blendSource = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)

    /** 原本的 RGB 混色目標。 */
    private val blendDestination = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)

    /** 原本的 alpha 混色來源。 */
    private val blendAlphaSource = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)

    /** 原本的 alpha 混色目標。 */
    private val blendAlphaDestination = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)

    /** 原本 shader 與色彩。 */
    private val shader = RenderSystem.getShader()

    /** 原本的 shader 色彩。 */
    private val color = RenderSystem.getShaderColor().copyOf()

    /** 原本的材質單元與 shader 材質。 */
    private val activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)

    /** 原本的 shader 材質索引。 */
    private val shaderTextures = IntArray(12) { RenderSystem.getShaderTexture(it) }

    /** 每個材質單元原本綁定的 2D 材質。 */
    private val boundTextures = IntArray(12) { unit ->
        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit)
        GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
    }

    /** 保留呼叫端原有的座標系；離屏圖片自行設定原點。 */
    private val modelView = ModelViewScope(RenderSystem.getModelViewStack())

    init {
        RenderSystem.activeTexture(activeTexture)
        RenderSystem.applyModelViewMatrix()
    }

    /** 還原這次模型繪製之前的圖形與實體繪製器狀態。 */
    override fun close() {
        dispatcher.setRotation(rotation)
        dispatcher.setRenderShadows(shadows)
        dispatcher.setRenderHitboxes(hitboxes)
        modelView.close()
        RenderSystem.applyModelViewMatrix()
        RenderSystem.setProjectionMatrix(projection, sorting)
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer)
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer)
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3])
        if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]) else RenderSystem.disableScissor()
        if (depth) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
        if (blend) RenderSystem.enableBlend() else RenderSystem.disableBlend()
        if (cull) RenderSystem.enableCull() else RenderSystem.disableCull()
        RenderSystem.depthMask(depthMask)
        RenderSystem.depthFunc(depthFunction)
        RenderSystem.blendFuncSeparate(blendSource, blendDestination, blendAlphaSource, blendAlphaDestination)
        RenderSystem.setShader { shader }
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3])
        shaderTextures.forEachIndexed { index, texture ->
            RenderSystem.setShaderTexture(index, texture)
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + index)
            RenderSystem.bindTexture(boundTextures[index])
        }
        RenderSystem.activeTexture(activeTexture)
    }

    /**
     * 保留呼叫端的矩陣，並在正常或例外結束時恢復。
     *
     * @property matrices 此次繪製使用的矩陣堆疊。
     */
    internal class ModelViewScope(private val matrices: MatrixStack) : AutoCloseable {
        init {
            matrices.push()
        }

        /** 恢復進入前的座標系，不將 GUI 位移清成單位矩陣。 */
        override fun close() {
            matrices.pop()
        }
    }
}
