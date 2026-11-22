package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import net.minecraft.client.color.item.ItemColor
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item

/**
 * 載入器提供的用戶端繪製與模型登記。
 *
 * 只在用戶端使用，由用戶端進入點呼叫；登記何時生效由載入器決定，但都在第一次繪製之前。
 */
interface ClientRegistrar {
    /**
     * 登記實體的繪製器。
     *
     * @param type 實體型別。
     * @param provider 建立繪製器的函式。
     */
    fun <T : Entity> registerEntityRenderer(type: EntityType<out T>, provider: EntityRendererProvider<T>)

    /**
     * 登記物品的顏色。
     *
     * @param color 依物品與圖層決定顏色的函式。
     * @param items 套用這個顏色的物品。
     */
    fun registerItemColor(color: ItemColor, items: Collection<Item>)

    /**
     * 登記物品模型依數值切換時使用的屬性。
     *
     * @param item 擁有這個屬性的物品。
     * @param id 屬性 ID，物品模型以它選擇覆寫。
     * @param property 依物品計算 0 到 1 之間的屬性值。
     */
    fun registerItemProperty(item: Item, id: ResourceLocation, property: ClampedItemPropertyFunction)

    /**
     * 登記以程式繪製的物品。
     *
     * @param item 要以程式繪製的物品。
     * @param renderer 繪製這個物品的函式。
     */
    fun registerItemRenderer(item: Item, renderer: ItemStackRenderer)

    /**
     * 登記參與每次資源重載的模型載入。
     *
     * @param loading 模型載入。
     */
    fun <T : Any> registerModelLoading(loading: ModelLoading<T>)
}
