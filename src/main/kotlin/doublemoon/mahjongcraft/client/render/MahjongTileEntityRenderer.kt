package doublemoon.mahjongcraft.client.render

import doublemoon.mahjongcraft.entity.MahjongTileEntity
import doublemoon.mahjongcraft.entity.MahjongTileEntity.Companion.MAHJONG_TILE_DEPTH
import doublemoon.mahjongcraft.entity.MahjongTileEntity.Companion.MAHJONG_TILE_HEIGHT
import doublemoon.mahjongcraft.entity.TileFacing
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongTile
import doublemoon.mahjongcraft.registry.ItemRegistry
import doublemoon.mahjongcraft.util.RenderHelper
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.rendereregistry.v1.EntityRendererRegistry
import net.minecraft.client.render.Frustum
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderDispatcher
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.model.json.ModelTransformation
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3f

@Environment(EnvType.CLIENT)
class MahjongTileEntityRenderer(
    dispatcher: EntityRenderDispatcher,
    context: EntityRendererRegistry.Context
) : EntityRenderer<MahjongTileEntity>(dispatcher) {

    private val itemRenderer = context.itemRenderer

    override fun shouldRender(entity: MahjongTileEntity, frustum: Frustum, x: Double, y: Double, z: Double): Boolean {
        return !entity.isInvisible && super.shouldRender(entity, frustum, x, y, z)
    }

    override fun render(
        entity: MahjongTileEntity,
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
            multiply(entity.facing.xpRotDegrees)
            var offsetY = 0.0
            var offsetZ = 0.0
            when (entity.facing) {  //偏移材質用
                TileFacing.HORIZONTAL -> offsetY = MAHJONG_TILE_HEIGHT / 2.0
                TileFacing.UP -> offsetZ = -MAHJONG_TILE_DEPTH / 2.0
                TileFacing.DOWN -> offsetZ = MAHJONG_TILE_DEPTH / 2.0
            }
            RenderHelper.renderItem(
                itemRenderer = itemRenderer,
                matrices = this,
                stack = mahjongTiles[entity.code],
                offsetX = 0.0,
                offsetY = offsetY,
                offsetZ = offsetZ,
                light = light,
                vertexConsumer = vertexConsumers,
                mode = ModelTransformation.Mode.HEAD
            )
            pop()
        }
    }

    override fun getTexture(entity: MahjongTileEntity): Identifier? = null

    companion object {
        /**
         * 所有的麻將牌物品, 先存著渲染的時候直接用, 省下每次實例化 ItemStack 的效能
         * */
        @Environment(EnvType.CLIENT)
        val mahjongTiles = MahjongTile.values()
            .map { ItemRegistry.mahjongTile.defaultStack.also { itemStack -> itemStack.damage = it.code } }
    }
}