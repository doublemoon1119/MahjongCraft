package com.doublemoon1119.mahjongcraft.platform.neoforge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ItemStackRenderer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions

/**
 * 以程式繪製物品的 NeoForge 用戶端擴充。
 *
 * NeoForge 以 [IClientItemExtensions] 讓物品提供自己的繪製器；每個登記的物品各有一份，繪製時交給它的 [ItemStackRenderer]。
 *
 * @property renderer 物品的繪製方式。
 */
internal class ItemRendererExtensions(private val renderer: ItemStackRenderer) : IClientItemExtensions {
    /** 原版物品繪製流程呼叫的繪製器，第一次需要時建立。 */
    private val itemRenderer: BlockEntityWithoutLevelRenderer by lazy { RegisteredItemRenderer(Minecraft.getInstance(), renderer) }

    override fun getCustomRenderer(): BlockEntityWithoutLevelRenderer = itemRenderer
}

/**
 * 把原版的物品繪製交給登記的 [ItemStackRenderer]。
 *
 * @param minecraft 用戶端。
 * @property renderer 物品的繪製方式。
 */
private class RegisteredItemRenderer(minecraft: Minecraft, private val renderer: ItemStackRenderer) : BlockEntityWithoutLevelRenderer(minecraft.blockEntityRenderDispatcher, minecraft.entityModels) {
    override fun renderByItem(
        stack: ItemStack,
        displayContext: ItemDisplayContext,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int,
    ) {
        renderer.render(stack, displayContext, poseStack, buffer, packedLight, packedOverlay)
    }
}
