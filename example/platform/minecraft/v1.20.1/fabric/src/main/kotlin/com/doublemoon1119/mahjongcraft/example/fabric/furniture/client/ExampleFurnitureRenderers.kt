package com.doublemoon1119.mahjongcraft.example.fabric.furniture.client

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureEntities
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureItems
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleChairSeatEntity
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleCushionEntity
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier

/** 範例家具 entity 的 client renderer 註冊入口。 */
object ExampleFurnitureRenderers {
    /** 登記範例椅子座位與坐墊的 client renderer。 */
    fun register() {
        EntityRendererRegistry.register(ExampleFurnitureEntities.chairSeat, ::ExampleChairSeatRenderer)
        EntityRendererRegistry.register(ExampleFurnitureEntities.cushion, ::ExampleCushionRenderer)
    }
}

/**
 * 不繪製隱形椅子座位 entity 的 renderer。
 *
 * @param context Minecraft renderer factory 提供的 client context。
 */
private class ExampleChairSeatRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<ExampleChairSeatEntity>(context) {
    /** 隱形 entity 沒有實際貼圖。 */
    override fun getTexture(entity: ExampleChairSeatEntity): Identifier? = null
}

/**
 * 使用坐墊物品模型繪製可保存坐墊 entity。
 *
 * @param context Minecraft renderer factory 提供的 client context。
 */
private class ExampleCushionRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<ExampleCushionEntity>(context) {
    /** 共用原版物品模型 renderer。 */
    private val itemRenderer = context.itemRenderer

    /** 將坐墊物品模型放在 entity 的中心。 */
    override fun render(
        entity: ExampleCushionEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        matrices.push()
        matrices.translate(0.0, 0.5, 0.0)
        itemRenderer.renderItem(
            ItemStack(ExampleFurnitureItems.cushion),
            ModelTransformationMode.NONE,
            light,
            OverlayTexture.DEFAULT_UV,
            matrices,
            vertexConsumers,
            entity.world,
            entity.id,
        )
        matrices.pop()
    }

    /** Item renderer 依 model 資源提供外觀。 */
    override fun getTexture(entity: ExampleCushionEntity): Identifier? = null
}
