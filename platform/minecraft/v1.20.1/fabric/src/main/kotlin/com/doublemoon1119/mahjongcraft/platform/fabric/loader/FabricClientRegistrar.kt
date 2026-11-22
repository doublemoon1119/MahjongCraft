package com.doublemoon1119.mahjongcraft.platform.fabric.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ItemStackRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModelLoading
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.color.item.ItemColor
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction
import net.minecraft.client.renderer.item.ItemProperties
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item

/** 以 Fabric API 實作 [ClientRegistrar]：呼叫當下就完成登記。 */
object FabricClientRegistrar : ClientRegistrar {
    override fun <T : Entity> registerEntityRenderer(type: EntityType<out T>, provider: EntityRendererProvider<T>) {
        EntityRendererRegistry.register(type, provider)
    }

    override fun registerItemColor(color: ItemColor, items: Collection<Item>) {
        ColorProviderRegistry.ITEM.register(color, *items.toTypedArray())
    }

    override fun registerItemProperty(item: Item, id: ResourceLocation, property: ClampedItemPropertyFunction) {
        ItemProperties.register(item, id, property)
    }

    override fun registerItemRenderer(item: Item, renderer: ItemStackRenderer) {
        BuiltinItemRendererRegistry.INSTANCE.register(item) { stack, mode, matrices, vertexConsumers, light, overlay ->
            renderer.render(stack, mode, matrices, vertexConsumers, light, overlay)
        }
    }

    override fun <T : Any> registerModelLoading(loading: ModelLoading<T>) {
        PreparableModelLoadingPlugin.register(
            { resourceManager, executor -> loading.prepare(resourceManager, executor) },
        ) { data, context ->
            context.addModels(loading.additionalModels(data))
            context.resolveModel().register { resolverContext -> loading.resolveModel(data, resolverContext.id()) }
        }
    }
}
