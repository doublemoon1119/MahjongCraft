package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.TablePropTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.screen.PlayerScreenHandler
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis

/**
 * 使用標準麻將牌模型繪製公開展示副本，不查詢私人手牌或正式牌張的可見性索引。
 *
 * @param context 原生 renderer 建立情境。
 * @property tileFaceRenderer 共用的牌面輔助標籤繪製器。
 */
class TablePropTileEntityRenderer(
    context: EntityRendererFactory.Context,
    private val tileFaceRenderer: MahjongTileFaceRenderer,
) : EntityRenderer<TablePropTileEntity>(context) {
    /**
     * 將公開副本平放在平台分配的位置，沿用規則指定的整套牌組外觀。
     *
     * @param entity 公開展示實體。
     * @param yaw 原生插值朝向。
     * @param tickDelta 本幀 tick 進度。
     * @param matrices 模型矩陣。
     * @param vertexConsumers 頂點緩衝來源。
     * @param light 世界光照。
     */
    override fun render(
        entity: TablePropTileEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        if (entity.isInvisible) return
        matrices.push()
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.yaw + 180f))
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f))
        matrices.translate(0.0, 0.0, -MahjongTileDimensions.TILE_DEPTH / 2)
        MahjongTileItemRenderer.renderWithAppearance(
            appearanceId = entity.appearanceId,
            assetKey = entity.assetKey,
            mode = ModelTransformationMode.HEAD,
            matrices = matrices,
            vertexConsumers = vertexConsumers,
            light = light,
            overlay = OverlayTexture.DEFAULT_UV,
        )
        tileFaceRenderer.renderModelLabels(entity.assetKey, matrices, vertexConsumers, light)
        matrices.pop()
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
    }

    /**
     * 材質由牌面模型自行選取，不使用單一實體貼圖。
     *
     * @param entity 要繪製的展示實體。
     * @return 牌面模型使用的方塊材質圖集。
     */
    override fun getTexture(entity: TablePropTileEntity): Identifier = PlayerScreenHandler.BLOCK_ATLAS_TEXTURE
}
