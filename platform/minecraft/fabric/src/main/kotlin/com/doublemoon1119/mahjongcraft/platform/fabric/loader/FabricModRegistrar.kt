package com.doublemoon1119.mahjongcraft.platform.fabric.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.RegisteredEntry
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab

/** 以 Fabric 的方式實作 [ModRegistrar]：呼叫當下就寫入登記表。 */
object FabricModRegistrar : ModRegistrar {
    override fun <R : Any, T : R> register(
        registry: ResourceKey<out Registry<R>>,
        id: ResourceLocation,
        factory: () -> T,
    ): RegisteredEntry<T> {
        @Suppress("UNCHECKED_CAST")
        val target = checkNotNull(BuiltInRegistries.REGISTRY.get(registry.location()) as Registry<R>?) {
            "Unknown registry ${registry.location()}"
        }
        val value = Registry.register(target, id, factory())
        return RegisteredEntry { value }
    }

    override fun afterRegistration(action: () -> Unit) {
        action()
    }

    override fun creativeModeTabBuilder(): CreativeModeTab.Builder = FabricItemGroup.builder()
}
