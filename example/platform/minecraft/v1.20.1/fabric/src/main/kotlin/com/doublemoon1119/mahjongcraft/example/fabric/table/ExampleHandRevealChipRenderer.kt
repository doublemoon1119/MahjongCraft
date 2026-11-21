package com.doublemoon1119.mahjongcraft.example.fabric.table

import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleTablePropDefinitions
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Blocks

/**
 * 使用原版金色方塊模型繪製範例攤牌籌碼。
 *
 * @param context 原生 renderer 建立情境。
 */
class ExampleHandRevealChipRenderer(
    context: EntityRendererProvider.Context,
) : EntityRenderer<ExampleHandRevealChipEntity>(context) {
    /** 原版方塊模型 renderer。 */
    private val blockRenderManager = context.blockRenderDispatcher

    /** 依平台給定的位置與水平角度繪製小型金色籌碼。 */
    override fun render(
        entity: ExampleHandRevealChipEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
    ) {
        matrices.pushPose()
        matrices.mulPose(Axis.YP.rotationDegrees(-entity.yRot))
        val dimensions = ExampleTablePropDefinitions.HAND_REVEAL_CHIP.dimensions
        matrices.scale(dimensions.width.toFloat(), dimensions.height.toFloat(), dimensions.depth.toFloat())
        matrices.translate(-0.5, 0.0, -0.5)
        blockRenderManager.renderSingleBlock(
            Blocks.GOLD_BLOCK.defaultBlockState(),
            matrices,
            vertexConsumers,
            light,
            OverlayTexture.NO_OVERLAY,
        )
        matrices.popPose()
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
    }

    /** 方塊 renderer 會自行選取材質。 */
    override fun getTextureLocation(entity: ExampleHandRevealChipEntity): ResourceLocation? = null
}
