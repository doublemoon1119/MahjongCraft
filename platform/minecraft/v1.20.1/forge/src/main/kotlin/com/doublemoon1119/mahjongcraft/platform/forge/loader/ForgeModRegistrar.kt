package com.doublemoon1119.mahjongcraft.platform.forge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.RegisteredEntry
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.registries.RegisterEvent

/**
 * 以 Forge 的登記事件實作 [ModRegistrar]：交出的項目先保留，Forge 輪到該登記表時才建立並寫入。
 *
 * 某個登記表的登記事件結束後，就不能再交出該登記表的項目。
 */
class ForgeModRegistrar : ModRegistrar {
    /** 尚未寫入的項目與登記完成後的工作。 */
    private val queue = RegistrationQueue<ResourceKey<out Registry<*>>, ResourceLocation>()

    /**
     * 接上本 mod 的事件匯流排：登記事件時寫入項目，共通設定階段在主執行緒執行登記完成後的工作。
     *
     * @param modBus 本 mod 的事件匯流排。
     */
    fun attach(modBus: IEventBus) {
        modBus.addListener(EventPriority.NORMAL, false, RegisterEvent::class.java) { event ->
            queue.take(event.registryKey).forEach { entry ->
                @Suppress("UNCHECKED_CAST")
                event.register(entry.registry as ResourceKey<Registry<Any>>, entry.id) { entry.create() }
            }
        }
        modBus.addListener(EventPriority.NORMAL, false, FMLCommonSetupEvent::class.java) { event ->
            event.enqueueWork(queue::runAfterRegistration)
        }
    }

    override fun <R : Any, T : R> register(
        registry: ResourceKey<out Registry<R>>,
        id: ResourceLocation,
        factory: () -> T,
    ): RegisteredEntry<T> {
        val entry = ForgeRegisteredEntry<T>(id)
        queue.add(registry = registry, id = id) { factory().also(entry::set) }
        return entry
    }

    override fun afterRegistration(action: () -> Unit) {
        queue.afterRegistration(action)
    }

    override fun creativeModeTabBuilder(): CreativeModeTab.Builder = CreativeModeTab.builder()
}

/**
 * Forge 寫入登記表後才有值的項目。
 *
 * @param T 登記的物件型別。
 * @property id 項目在登記表中的 ID，用於錯誤訊息。
 */
private class ForgeRegisteredEntry<T : Any>(private val id: ResourceLocation) : RegisteredEntry<T> {
    /** 寫入登記表的物件；寫入前為 null。 */
    @Volatile
    private var value: T? = null

    /**
     * 記錄寫入登記表的物件。
     *
     * @param registered 寫入的物件。
     */
    fun set(registered: T) {
        value = registered
    }

    override fun get(): T = checkNotNull(value) { "$id is not registered yet" }
}
