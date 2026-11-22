package com.doublemoon1119.mahjongcraft.example.fabric.furniture.client

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureEntities
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureItems
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleChairSeatEntity
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.entity.ExampleCushionEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

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
    context: EntityRendererProvider.Context,
) : EntityRenderer<ExampleChairSeatEntity>(context) {
    /** 隱形 entity 沒有實際貼圖；原版規定回傳值不可為空，因此回傳方塊材質圖集。 */
    override fun getTextureLocation(entity: ExampleChairSeatEntity): ResourceLocation = InventoryMenu.BLOCK_ATLAS
}

/**
 * 使用坐墊物品模型繪製可保存坐墊 entity。
 *
 * @param context Minecraft renderer factory 提供的 client context。
 */
private class ExampleCushionRenderer(
    context: EntityRendererProvider.Context,
) : EntityRenderer<ExampleCushionEntity>(context) {
    /** 共用原版物品模型 renderer。 */
    private val itemRenderer = context.itemRenderer

    /** 將坐墊物品模型放在 entity 的中心。 */
    override fun render(
        entity: ExampleCushionEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        matrices.pushPose()
        matrices.translate(0.0, 0.5, 0.0)
        itemRenderer.renderStatic(
            ItemStack(ExampleFurnitureItems.cushion),
            ItemDisplayContext.NONE,
            light,
            OverlayTexture.NO_OVERLAY,
            matrices,
            vertexConsumers,
            entity.level(),
            entity.id,
        )
        matrices.popPose()
    }

    /** Item renderer 依 model 資源提供外觀；原版規定回傳值不可為空，因此回傳方塊材質圖集。 */
    override fun getTextureLocation(entity: ExampleCushionEntity): ResourceLocation = InventoryMenu.BLOCK_ATLAS
}
