package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

/** 以程式繪製的物品；物品模型的 parent 必須是 `builtin/entity`，載入器才會改用這裡繪製。 */
fun interface ItemStackRenderer {
    /**
     * 繪製一個物品。
     *
     * @param stack 要繪製的物品。
     * @param mode 物品出現的位置，例如手上、物品欄或掉落物。
     * @param matrices 目前的座標轉換。
     * @param vertexConsumers 繪製緩衝。
     * @param light 光照值。
     * @param overlay 受傷閃紅等覆蓋效果的值。
     */
    fun render(
        stack: ItemStack,
        mode: ItemDisplayContext,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        overlay: Int,
    )
}
