package doublemoon.mahjongcraft.client.render

import doublemoon.mahjongcraft.entity.MahjongScoringStickEntity
import doublemoon.mahjongcraft.entity.MahjongScoringStickEntity.Companion.MAHJONG_POINT_STICK_HEIGHT
import doublemoon.mahjongcraft.entity.MahjongScoringStickEntity.Companion.MAHJONG_POINT_STICK_SCALE
import doublemoon.mahjongcraft.game.mahjong.riichi.ScoringStick
import doublemoon.mahjongcraft.registry.ItemRegistry
import doublemoon.mahjongcraft.util.RenderHelper
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.render.Frustum
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.model.json.ModelTransformation
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3f

@Environment(EnvType.CLIENT)
class MahjongScoringStickEntityRenderer(
    context: EntityRendererFactory.Context
) : EntityRenderer<MahjongScoringStickEntity>(context) {

    private val itemRenderer = context.itemRenderer

    override fun shouldRender(
        entity: MahjongScoringStickEntity,
        frustum: Frustum,
        x: Double,
        y: Double,
        z: Double
    ): Boolean {
        return !entity.isInvisible && super.shouldRender(entity, frustum, x, y, z)
    }

    override fun render(
        entity: MahjongScoringStickEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        with(matrices) {
            push()
            multiply(Vec3f.POSITIVE_Y.getDegreesQuaternion(-entity.yaw + 180))
            RenderHelper.renderItem(
                itemRenderer = itemRenderer,
                matrices = this,
                stack = scoringSticks[entity.code],
                offsetX = 0.0,
                offsetY = MAHJONG_POINT_STICK_HEIGHT / 2.0 + (1f * MAHJONG_POINT_STICK_SCALE - MAHJONG_POINT_STICK_HEIGHT) / 2.0,
                offsetZ = 0.0,
                light = light,
                mode = ModelTransformation.Mode.HEAD,
                vertexConsumer = vertexConsumers
            )
            pop()
        }
    }

    override fun getTexture(entity: MahjongScoringStickEntity): Identifier? = null

    companion object {
        private val scoringSticks = ScoringStick.values()
            .map { ItemRegistry.mahjongScoringStick.defaultStack.also { itemStack -> itemStack.damage = it.code } }
    }
}