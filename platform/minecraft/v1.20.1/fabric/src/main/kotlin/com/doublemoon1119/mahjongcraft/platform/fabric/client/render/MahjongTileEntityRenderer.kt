package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ALL_TILE_ASSET_KEYS
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis

/** 使用既有麻將牌 item model 呈現自由放置牌張的 client renderer。 */
class MahjongTileEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<MahjongTileEntity>(context) {
    /** 共用 Vanilla item renderer，避免建立第二套牌面模型格式。 */
    private val itemRenderer = context.itemRenderer

    /** 依 entity yaw 與姿態旋轉模型，並補償原點使牌底貼齊所在表面。 */
    override fun render(
        entity: MahjongTileEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        matrices.push()
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.yaw + 180.0f))
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.tilePose.rotationDegrees))
        when (entity.tilePose) {
            MahjongTilePose.STANDING -> matrices.translate(0.0, MahjongTileEntity.TILE_HEIGHT / 2.0, 0.0)
            MahjongTilePose.FACE_UP -> matrices.translate(0.0, 0.0, -MahjongTileEntity.TILE_DEPTH / 2.0)
            MahjongTilePose.FACE_DOWN -> matrices.translate(0.0, 0.0, MahjongTileEntity.TILE_DEPTH / 2.0)
        }
        itemRenderer.renderItem(
            tileStacks.getValue(entity.tileAssetKey),
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
    override fun getTexture(entity: MahjongTileEntity): Identifier? = null

    companion object {
        /** 每個合法 asset key 共用一個只供渲染使用的 ItemStack。 */
        private val tileStacks: Map<String, ItemStack> = ALL_TILE_ASSET_KEYS.associateWith { assetKey ->
            ItemStack(ModItems.MAHJONG_TILE).also { MahjongTileItem.writeTileAssetKey(it, assetKey) }
        }
    }
}

/** 轉成以局部 X 軸為基準的 renderer 旋轉角度。 */
private val MahjongTilePose.rotationDegrees: Float
    get() = when (this) {
        MahjongTilePose.STANDING -> 0.0f
        MahjongTilePose.FACE_UP -> 90.0f
        MahjongTilePose.FACE_DOWN -> -90.0f
    }
