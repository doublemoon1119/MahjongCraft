package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleRegistrar
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab

/** 以 Fabric 的方式實作 [ExampleRegistrar]：呼叫當下就寫入原版登記表。 */
object FabricExampleRegistrar : ExampleRegistrar {
    override fun <T : Any> register(registry: ResourceKey<out Registry<in T>>, id: ResourceLocation, factory: () -> T, onRegistered: (T) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        val target = checkNotNull(BuiltInRegistries.REGISTRY.get(registry.location()) as Registry<in T>?) { "Unknown registry ${registry.location()}" }
        onRegistered(Registry.register(target, id, factory()))
    }

    override fun creativeModeTabBuilder(): CreativeModeTab.Builder = FabricItemGroup.builder()
}
