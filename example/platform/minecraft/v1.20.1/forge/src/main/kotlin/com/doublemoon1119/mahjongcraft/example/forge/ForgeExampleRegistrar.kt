package com.doublemoon1119.mahjongcraft.example.forge

import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleRegistrar
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister

/**
 * 以 Forge 的 [DeferredRegister] 實作 [ExampleRegistrar]：交出的物件在 Forge 輪到該登記表時才建立並寫入，寫入後交回。
 *
 * 每個登記表各用一個 [DeferredRegister]，依第一次登記的順序建立。全部交出後以 [attach] 接上本 mod 的事件匯流排。
 *
 * @property modId 登記的物件所屬的 mod，也是每個物件 ID 必須使用的命名空間。
 */
class ForgeExampleRegistrar(private val modId: String) : ExampleRegistrar {
    /** 各登記表的延後登記器。 */
    private val registers = linkedMapOf<ResourceKey<*>, DeferredRegister<*>>()

    /** 是否已接上事件匯流排。接上後不能再登記。 */
    private var attached = false

    override fun <T : Any> register(registry: ResourceKey<out Registry<in T>>, id: ResourceLocation, factory: () -> T, onRegistered: (T) -> Unit) {
        check(!attached) { "$id is registered after the example registrar was attached" }
        require(id.namespace == modId) { "$id is not in the $modId namespace" }
        @Suppress("UNCHECKED_CAST")
        val register = registers.getOrPut(registry) { DeferredRegister.create(registry as ResourceKey<out Registry<Any>>, modId) } as DeferredRegister<Any>
        register.register(id.path) { factory().also(onRegistered) }
    }

    override fun creativeModeTabBuilder(): CreativeModeTab.Builder = CreativeModeTab.builder()

    /**
     * 讓每個延後登記器在 Forge 的登記事件中寫入物件。
     *
     * @param modBus 本 mod 的事件匯流排。
     */
    fun attach(modBus: IEventBus) {
        check(!attached) { "The example registrar is already attached" }
        attached = true
        registers.values.forEach { it.register(modBus) }
    }
}
