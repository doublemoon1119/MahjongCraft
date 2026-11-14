package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRulerEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.ruler.MahjongRulerDimensions
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis

/**
 * 把牌尺畫成半透明的純色長方條，長邊與放置者的視線垂直，依擺法平放或豎放，底面貼齊實體位置。
 *
 * @param context Minecraft renderer factory 提供的 client context。
 */
class MahjongRulerEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<MahjongRulerEntity>(context) {
    override fun render(
        entity: MahjongRulerEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        if (entity.isInvisible) return
        matrices.push()
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.yaw))
        val rgb = MahjongRulerTint.rgb(entity.color)
        val posture = entity.posture
        RulerCuboid(
            top = MahjongRulerDimensions.height(posture).toFloat(),
            halfWidth = (MahjongRulerDimensions.width(posture) / 2.0).toFloat(),
            consumer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentCull(TEXTURE)),
            entry = matrices.peek(),
            red = (rgb shr RED_SHIFT) and CHANNEL_MASK,
            green = (rgb shr GREEN_SHIFT) and CHANNEL_MASK,
            blue = rgb and CHANNEL_MASK,
            alpha = MahjongRulerTint.alpha(entity.color),
            light = light,
        ).draw()
        matrices.pop()
    }

    /** 牌尺使用一張純白貼圖，顏色由頂點決定。 */
    override fun getTexture(entity: MahjongRulerEntity): Identifier = TEXTURE

    /**
     * 一次繪製用的長方條；六個面都以外側為正面。
     *
     * @property top 頂面高度。
     * @property halfWidth 與長邊垂直的半寬。
     * @property consumer 寫入頂點的緩衝。
     * @property entry 目前的變換矩陣。
     * @property red 紅色通道。
     * @property green 綠色通道。
     * @property blue 藍色通道。
     * @property alpha 不透明度。
     * @property light 光照值。
     */
    private class RulerCuboid(
        val top: Float,
        val halfWidth: Float,
        val consumer: VertexConsumer,
        val entry: MatrixStack.Entry,
        val red: Int,
        val green: Int,
        val blue: Int,
        val alpha: Int,
        val light: Int,
    ) {
        /** 依序畫出六個面。 */
        fun draw() {
            val y1 = top
            val z0 = -halfWidth
            val z1 = halfWidth
            face(0f, 1f, 0f, listOf(Triple(X0, y1, z0), Triple(X0, y1, z1), Triple(X1, y1, z1), Triple(X1, y1, z0)))
            face(0f, -1f, 0f, listOf(Triple(X0, Y0, z0), Triple(X1, Y0, z0), Triple(X1, Y0, z1), Triple(X0, Y0, z1)))
            face(0f, 0f, -1f, listOf(Triple(X0, Y0, z0), Triple(X0, y1, z0), Triple(X1, y1, z0), Triple(X1, Y0, z0)))
            face(0f, 0f, 1f, listOf(Triple(X0, Y0, z1), Triple(X1, Y0, z1), Triple(X1, y1, z1), Triple(X0, y1, z1)))
            face(-1f, 0f, 0f, listOf(Triple(X0, Y0, z0), Triple(X0, Y0, z1), Triple(X0, y1, z1), Triple(X0, y1, z0)))
            face(1f, 0f, 0f, listOf(Triple(X1, Y0, z0), Triple(X1, y1, z0), Triple(X1, y1, z1), Triple(X1, Y0, z1)))
        }

        /**
         * 畫一個面。
         *
         * @param normalX 法向量 X。
         * @param normalY 法向量 Y。
         * @param normalZ 法向量 Z。
         * @param corners 從外側看逆時針排列的四個角。
         */
        private fun face(normalX: Float, normalY: Float, normalZ: Float, corners: List<Triple<Float, Float, Float>>) {
            corners.zip(FACE_UVS).forEach { (corner, uv) ->
                consumer.vertex(entry.positionMatrix, corner.first, corner.second, corner.third)
                    .color(red, green, blue, alpha)
                    .texture(uv.first, uv.second)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(entry.normalMatrix, normalX, normalY, normalZ)
                    .next()
            }
        }
    }

    private companion object {
        /** 牌尺的純白貼圖。 */
        val TEXTURE: Identifier = Identifier(MinecraftModMetadata.MOD_ID, "textures/entity/mahjong_ruler.png")

        /** 長邊的一端。 */
        const val X0: Float = (-MahjongRulerDimensions.LENGTH / 2.0).toFloat()

        /** 長邊的另一端。 */
        const val X1: Float = (MahjongRulerDimensions.LENGTH / 2.0).toFloat()

        /** 底面。 */
        const val Y0: Float = 0.0f

        /** 每個面四個角的貼圖座標。 */
        val FACE_UVS: List<Pair<Float, Float>> = listOf(0f to 0f, 0f to 1f, 1f to 1f, 1f to 0f)

        /** 紅色通道在打包顏色中的位移。 */
        const val RED_SHIFT: Int = 16

        /** 綠色通道在打包顏色中的位移。 */
        const val GREEN_SHIFT: Int = 8

        /** 單一顏色通道的遮罩。 */
        const val CHANNEL_MASK: Int = 0xFF
    }
}
