package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.ModelViewMatrix
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import org.joml.Matrix3f
import org.joml.Matrix4f

/**
 * 世界中的面板等整個世界畫完才畫（[ClientEvents.onWorldRenderFinished]），依離鏡頭的距離由遠到近繪製。
 *
 * - 面板與牌、玩家同在實體階段畫時，原版不依遠近排序實體，面板後方較晚畫的實體會被擋住。
 *   延到最後畫，面板的半透明底色就能透出後方的一切。
 * - 開了光影時，光影模組畫完世界後才補上陰影與後製。面板等這些都完成才畫，不會被玩家或方塊的影子蓋到，外觀與沒開光影時相同。
 * - 光影的陰影那一輪不畫面板，面板不在桌面投下影子。
 * - 不在世界繪製期間時（例如牌譜回放的畫面場景）照常立刻畫。
 */
internal object WorldPanelDeferral {
    /** 這一幀收集的面板。 */
    private val queue = WorldPanelQueue()

    /**
     * 登記世界繪製的開始與畫完。
     *
     * @param events 登記用戶端事件用的事件來源。
     */
    fun register(events: ClientEvents) {
        events.onWorldRenderStart { queue.beginFrame() }
        events.onWorldRenderFinished { drawPending() }
    }

    /**
     * 畫一個面板：世界繪製期間記下目前的座標轉換，等世界畫完再畫。其他時候立刻畫。
     *
     * @param matrices 面板位置的座標轉換。
     * @param consumers 立刻畫時使用的繪製緩衝來源。
     * @param panel 以指定的座標轉換與繪製緩衝畫出整個面板。
     */
    fun draw(matrices: PoseStack, consumers: MultiBufferSource, panel: (PoseStack, MultiBufferSource) -> Unit) {
        if (ShaderShadowPass.active()) return
        val entry = matrices.last()
        val (position, normal) = cameraSpaceTransform(RenderSystem.getModelViewMatrix(), entry.pose(), entry.normal())
        val distanceSquared = position.m30().toDouble().let { it * it } +
            position.m31().toDouble().let { it * it } +
            position.m32().toDouble().let { it * it }
        val queued = queue.submit(distanceSquared) {
            val stack = PoseStack()
            stack.last().pose().set(position)
            stack.last().normal().set(normal)
            val immediate = Minecraft.getInstance().renderBuffers().bufferSource()
            panel(stack, immediate)
            immediate.endBatch()
        }
        if (!queued) panel(matrices, consumers)
    }

    /**
     * 以鏡頭為原點、鏡頭轉向為座標軸的座標轉換。
     *
     * - 鏡頭的轉向依 Minecraft 版本，放在實體的座標轉換，或繪製狀態的模型檢視矩陣。
     * - 兩者相乘後，不論哪一種都得到完整的轉換。
     *
     * @param modelView 繪製狀態目前的模型檢視矩陣。
     * @param pose 面板位置的座標轉換。
     * @param normal 面板位置的法向量轉換。
     * @return 新的座標轉換與法向量轉換。不改動傳入的矩陣。
     */
    internal fun cameraSpaceTransform(modelView: Matrix4f, pose: Matrix4f, normal: Matrix3f): Pair<Matrix4f, Matrix3f> = Matrix4f(modelView).mul(pose) to Matrix3f().set(modelView).mul(normal)

    /**
     * 畫出這一幀收集的面板。
     *
     * 記下的座標轉換已經包含鏡頭的轉向（[cameraSpaceTransform]），所以畫的期間把繪製狀態的模型檢視矩陣設為單位矩陣。
     */
    private fun drawPending() {
        val panels = queue.drain()
        if (panels.isEmpty()) return
        ModelViewMatrix.push()
        ModelViewMatrix.setIdentity()
        ModelViewMatrix.apply()
        try {
            panels.forEach { it() }
        } finally {
            ModelViewMatrix.pop()
            ModelViewMatrix.apply()
        }
    }
}
