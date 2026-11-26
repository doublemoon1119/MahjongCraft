package com.doublemoon1119.mahjongcraft.platform.neoforge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ItemStackRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModelLoading
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModelLoadings
import net.minecraft.client.Minecraft
import net.minecraft.client.color.item.ItemColor
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction
import net.minecraft.client.renderer.item.ItemProperties
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.ModelEvent
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent

/**
 * 以 NeoForge 的用戶端登記事件實作 [ClientRegistrar]：交出的項目先保留，對應的事件發生時才登記。
 *
 * - 物品繪製以 NeoForge 的物品用戶端擴充提供。
 * - 程式提供的模型由 [ModelLoadings] 與 NeoForge 專用的 mixin 處理，額外的模型以獨立模型登記。
 */
class NeoForgeClientRegistrar : ClientRegistrar {
    /** 等待實體繪製登記事件的登記。 */
    private val entityRenderers = mutableListOf<(EntityRenderersEvent.RegisterRenderers) -> Unit>()

    /** 等待物品顏色登記事件的登記。 */
    private val itemColors = mutableListOf<(RegisterColorHandlersEvent.Item) -> Unit>()

    /** 等待用戶端設定階段的物品屬性登記。 */
    private val itemProperties = mutableListOf<() -> Unit>()

    /** 等待物品用戶端擴充登記事件的物品繪製。 */
    private val itemRenderers = mutableListOf<(RegisterClientExtensionsEvent) -> Unit>()

    /**
     * 接上本 mod 的事件匯流排。
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
        modBus.addListener(EventPriority.NORMAL, false, RegisterClientExtensionsEvent::class.java) { event ->
            itemRenderers.forEach { it(event) }
            itemRenderers.clear()
        }
        modBus.addListener(EventPriority.NORMAL, false, FMLClientSetupEvent::class.java) { event ->
            val pending = itemProperties.toList()
            itemProperties.clear()
            event.enqueueWork { pending.forEach { it() } }
        }
        modBus.addListener(EventPriority.NORMAL, false, ModelEvent.RegisterAdditional::class.java) { event ->
            ModelLoadings.prepare(Minecraft.getInstance().resourceManager).forEach { id -> event.register(ModelResourceLocation.standalone(id)) }
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
        itemRenderers += { event -> event.registerItem(ItemRendererExtensions(renderer), item) }
    }

    override fun <T : Any> registerModelLoading(loading: ModelLoading<T>) {
        ModelLoadings.add(loading)
    }
}
