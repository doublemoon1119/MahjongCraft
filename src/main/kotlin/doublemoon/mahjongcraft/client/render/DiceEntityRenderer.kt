package doublemoon.mahjongcraft.client.render

import doublemoon.mahjongcraft.entity.DiceEntity
import doublemoon.mahjongcraft.entity.DiceEntity.Companion.DICE_HEIGHT
import doublemoon.mahjongcraft.entity.DiceEntity.Companion.DICE_SCALE
import doublemoon.mahjongcraft.entity.DicePoint
import doublemoon.mahjongcraft.registry.ItemRegistry
import doublemoon.mahjongcraft.util.RenderHelper
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.render.Frustum
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis

@Environment(EnvType.CLIENT)
class DiceEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<DiceEntity>(context) {

    private val itemRenderer = context.itemRenderer

    override fun shouldRender(entity: DiceEntity, frustum: Frustum, x: Double, y: Double, z: Double): Boolean {
        return !entity.isInvisible && super.shouldRender(entity, frustum, x, y, z)
    }

    override fun render(
        entity: DiceEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        with(matrices) {
            push()
            multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.yaw + 180))
            multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.point.xpRotDegrees))
            multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.point.ypRotDegrees))
            val baseOffset = DICE_HEIGHT / 2.0 + (1f * DICE_SCALE - DICE_HEIGHT) / 2.0
            val halfHeight = DICE_HEIGHT / 2.0
            val offsetX = when (entity.point) {
                DicePoint.THREE -> halfHeight
                DicePoint.FIVE -> -halfHeight
                else -> 0.0
            }
            val offsetY = baseOffset - when (entity.point) {
                DicePoint.ONE -> 0.0
                DicePoint.SIX -> DICE_HEIGHT.toDouble()
                else -> halfHeight
            }
            val offsetZ = when (entity.point) {
                DicePoint.TWO -> -halfHeight
                DicePoint.FOUR -> halfHeight
                else -> 0.0
            }
            RenderHelper.renderItem(
                itemRenderer = itemRenderer,
                matrices = matrices,
                offsetX = offsetX,
                offsetY = offsetY,
                offsetZ = offsetZ,
                stack = diceItem,
                light = light,
                vertexConsumer = vertexConsumers,
            )
            pop()
        }
    }

    override fun getTexture(entity: DiceEntity): Identifier? = null

    companion object {
        private val diceItem = ItemRegistry.dice.defaultStack
    }
}