package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDiceEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis

/** 使用單一骰子 item model 呈現指定朝上點數的 client renderer。 */
class MahjongDiceEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<MahjongDiceEntity>(context) {
    /** 共用 Vanilla item renderer，避免建立另一套 cube model loader。 */
    private val itemRenderer = context.itemRenderer

    /** 依 entity yaw 與點數姿態旋轉模型，並讓骰子底面貼齊 entity 位置。 */
    override fun render(
        entity: MahjongDiceEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        matrices.push()
        matrices.translate(0.0, MahjongDiceEntity.SIZE / 2.0, 0.0)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.yaw + 180.0f))
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.point.xRotationDegrees))
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.point.yRotationDegrees))
        matrices.scale(DICE_MODEL_SCALE, DICE_MODEL_SCALE, DICE_MODEL_SCALE)
        matrices.translate(0.0, DICE_MODEL_CENTER_OFFSET_Y, 0.0)
        itemRenderer.renderItem(
            DICE_STACK,
            ModelTransformationMode.HEAD,
            light,
            OverlayTexture.DEFAULT_UV,
            matrices,
            vertexConsumers,
            entity.world,
            entity.id,
        )
        matrices.pop()
    }

    /** ItemRenderer 自行解析材質，因此 entity renderer 不提供單一 texture。 */
    override fun getTexture(entity: MahjongDiceEntity): Identifier? = null

    companion object {
        /** 舊模型由 `10/16 block` 縮放成 `0.125 block` 所需比例。 */
        private const val DICE_MODEL_SCALE = 0.2f

        /** 將舊模型實際中心 `Y = 5` 對齊 item renderer 使用的 `Y = 8` 旋轉原點。 */
        private const val DICE_MODEL_CENTER_OFFSET_Y = 3.0 / 16.0

        /** 供所有骰子 entity 共用的渲染 stack。 */
        private val DICE_STACK = ItemStack(ModItems.MAHJONG_DICE)
    }
}
