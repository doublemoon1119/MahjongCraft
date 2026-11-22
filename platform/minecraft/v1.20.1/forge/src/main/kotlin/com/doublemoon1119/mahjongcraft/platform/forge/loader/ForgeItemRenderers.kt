package com.doublemoon1119.mahjongcraft.platform.forge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ItemStackRenderer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraftforge.client.extensions.common.IClientItemExtensions
import java.util.concurrent.ConcurrentHashMap

/**
 * 以程式繪製的物品。
 *
 * Forge 只向物品自己的類別詢問繪製方式，因此由 Forge 專用的 mixin 替這類物品加上 Forge 要的方法，並在其中交出
 * [clientExtensions]；繪製時才依物品找出登記的繪製方式。
 */
object ForgeItemRenderers {
    /** 依物品登記的繪製方式。 */
    private val renderers = ConcurrentHashMap<Item, ItemStackRenderer>()

    /**
     * 登記物品的繪製方式。
     *
     * @param item 以程式繪製的物品。
     * @param renderer 繪製方式。
     */
    fun register(item: Item, renderer: ItemStackRenderer) {
        renderers[item] = renderer
    }

    /**
     * 取得物品登記的繪製方式。
     *
     * @param item 要繪製的物品。
     * @return 登記的繪製方式；沒有時為 null。
     */
    internal fun find(item: Item): ItemStackRenderer? = renderers[item]

    /**
     * 交給 Forge 的物品繪製設定；只由 Forge 專用的 mixin 在物品建立時取用。
     *
     * @return 以登記的繪製方式繪製物品的設定。
     */
    @JvmStatic
    fun clientExtensions(): IClientItemExtensions = RegisteredItemExtensions
}

/** 繪製時才建立原版的物品繪製器，物品建立時用戶端可能還沒準備好。 */
private object RegisteredItemExtensions : IClientItemExtensions {
    /** 以登記的繪製方式繪製物品的原版繪製器。 */
    private val renderer: BlockEntityWithoutLevelRenderer by lazy {
        val minecraft = Minecraft.getInstance()
        RegisteredItemRenderer(minecraft)
    }

    override fun getCustomRenderer(): BlockEntityWithoutLevelRenderer = renderer
}

/**
 * 把原版物品繪製轉交給登記的繪製方式。
 *
 * @param minecraft 提供原版繪製器需要的方塊實體繪製與模型。
 */
private class RegisteredItemRenderer(minecraft: Minecraft) : BlockEntityWithoutLevelRenderer(minecraft.blockEntityRenderDispatcher, minecraft.entityModels) {
    override fun renderByItem(
        stack: ItemStack,
        displayContext: ItemDisplayContext,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int,
    ) {
        ForgeItemRenderers.find(stack.item)?.render(stack, displayContext, poseStack, buffer, packedLight, packedOverlay)
    }
}
