package com.doublemoon1119.mahjongcraft.example.fabric.table

import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleTablePropDefinitions
import net.minecraft.block.Blocks
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis

/**
 * 使用原版金色方塊模型繪製範例宣告籌碼。
 *
 * @param context 原生 renderer 建立情境。
 */
class ExampleDeclarationChipRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<ExampleDeclarationChipEntity>(context) {
    /** 原版方塊模型 renderer。 */
    private val blockRenderManager = context.blockRenderManager

    /** 依平台給定的位置與水平角度繪製小型金色籌碼。 */
    override fun render(
        entity: ExampleDeclarationChipEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        matrices.push()
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.yaw))
        val dimensions = ExampleTablePropDefinitions.DECLARATION_CHIP.dimensions
        matrices.scale(dimensions.width.toFloat(), dimensions.height.toFloat(), dimensions.depth.toFloat())
        matrices.translate(-0.5, 0.0, -0.5)
        blockRenderManager.renderBlockAsEntity(
            Blocks.GOLD_BLOCK.defaultState,
            matrices,
            vertexConsumers,
            light,
            OverlayTexture.DEFAULT_UV,
        )
        matrices.pop()
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
    }

    /** 方塊 renderer 會自行選取材質。 */
    override fun getTexture(entity: ExampleDeclarationChipEntity): Identifier? = null
}
