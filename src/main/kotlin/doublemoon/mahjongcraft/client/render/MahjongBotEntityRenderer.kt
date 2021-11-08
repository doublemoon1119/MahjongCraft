package doublemoon.mahjongcraft.client.render

import doublemoon.mahjongcraft.entity.MahjongBotEntity
import doublemoon.mahjongcraft.entity.MahjongTileEntity
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
class MahjongBotEntityRenderer(
    context: EntityRendererFactory.Context
) : EntityRenderer<MahjongBotEntity>(context) {

    private val itemRenderer = context.itemRenderer

    override fun shouldRender(entity: MahjongBotEntity, frustum: Frustum, x: Double, y: Double, z: Double): Boolean {
        return !entity.isInvisible && super.shouldRender(entity, frustum, x, y, z)
    }

    override fun render(
        entity: MahjongBotEntity,
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
            val scale = MahjongBotEntity.MAHJONG_BOT_HEIGHT / MahjongTileEntity.MAHJONG_TILE_HEIGHT
            scale(scale, scale, scale)
            RenderHelper.renderItem(
                itemRenderer = itemRenderer,
                matrices = this,
                stack = MahjongTileEntityRenderer.mahjongTiles[entity.code],
                offsetX = 0.0,
                offsetY = MahjongBotEntity.MAHJONG_BOT_HEIGHT / 2.0 / scale,
                offsetZ = 0.0,
                light = light,
                vertexConsumer = vertexConsumers,
                mode = ModelTransformation.Mode.HEAD
            )
            pop()
        }
    }

    override fun getTexture(entity: MahjongBotEntity): Identifier? = null
}