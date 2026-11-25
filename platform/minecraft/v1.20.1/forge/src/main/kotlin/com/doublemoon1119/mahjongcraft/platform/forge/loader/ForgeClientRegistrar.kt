package com.doublemoon1119.mahjongcraft.platform.forge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ItemStackRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModelLoading
import net.minecraft.client.Minecraft
import net.minecraft.client.color.item.ItemColor
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction
import net.minecraft.client.renderer.item.ItemProperties
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.client.event.ModelEvent
import net.minecraftforge.client.event.RegisterColorHandlersEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent

/**
 * 以 Forge 的登記事件實作 [ClientRegistrar]：登記先保留，Forge 發出對應的登記事件時才套用。
 *
 * 實體繪製器與物品顏色在各自的登記事件中加入，物品屬性在用戶端設定階段的主執行緒加入。以程式繪製的物品與模型載入
 * 分別由 [ForgeItemRenderers] 與 [ForgeModelLoadings] 在 Forge 需要時取用。
 */
class ForgeClientRegistrar : ClientRegistrar {
    /** 等待實體繪製器登記事件的登記。 */
    private val entityRenderers = mutableListOf<(EntityRenderersEvent.RegisterRenderers) -> Unit>()

    /** 等待物品顏色登記事件的登記。 */
    private val itemColors = mutableListOf<(RegisterColorHandlersEvent.Item) -> Unit>()

    /** 等待用戶端設定階段的物品屬性登記。 */
    private val itemProperties = mutableListOf<() -> Unit>()

    /**
     * 接上本 mod 的事件匯流排，在 Forge 的登記事件中套用保留的登記。
     *
     * @param modBus 本 mod 的事件匯流排。
     */
    fun attach(modBus: IEventBus) {
        modBus.addListener(EventPriority.NORMAL, false, EntityRenderersEvent.RegisterRenderers::class.java) { event ->
            entityRenderers.forEach { it(event) }
            entityRenderers.clear()
        }
        modBus.addListener(EventPriority.NORMAL, false, RegisterColorHandlersEvent.Item::class.java) { event ->
            itemColors.forEach { it(event) }
            itemColors.clear()
        }
        modBus.addListener(EventPriority.NORMAL, false, FMLClientSetupEvent::class.java) { event ->
            val pending = itemProperties.toList()
            itemProperties.clear()
            event.enqueueWork { pending.forEach { it() } }
        }
        modBus.addListener(EventPriority.NORMAL, false, ModelEvent.RegisterAdditional::class.java) { event ->
            ForgeModelLoadings.prepare(Minecraft.getInstance().resourceManager).forEach(event::register)
        }
    }

    override fun <T : Entity> registerEntityRenderer(type: EntityType<out T>, provider: EntityRendererProvider<T>) {
        entityRenderers += { event -> event.registerEntityRenderer(type, provider) }
    }

    override fun registerItemColor(color: ItemColor, items: Collection<Item>) {
        itemColors += { event -> event.register(color, *items.toTypedArray()) }
    }

    override fun registerItemProperty(item: Item, id: ResourceLocation, property: ClampedItemPropertyFunction) {
        itemProperties += { ItemProperties.register(item, id, property) }
    }

    override fun registerItemRenderer(item: Item, renderer: ItemStackRenderer) {
        ForgeItemRenderers.register(item, renderer)
    }

    override fun <T : Any> registerModelLoading(loading: ModelLoading<T>) {
        ForgeModelLoadings.add(loading)
    }
}
