package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab

/**
 * 載入器提供的 Minecraft 登記表寫入方式。
 *
 * 有的載入器在呼叫當下就寫入登記表，有的要等到各登記表輪到時才寫入，因此登記時交出建立物件的函式，
 * 由載入器在寫入時才呼叫；物件只在建立函式裡建立，讀取一律透過 [RegisteredEntry]。
 */
interface ModRegistrar {
    /**
     * 登記一個物件。
     *
     * @param registry 要寫入的登記表。
     * @param id 物件在登記表中的 ID。
     * @param factory 建立物件的函式；載入器寫入登記表時呼叫一次。
     * @return 登記完成後可讀取物件的項目。
     */
    fun <R : Any, T : R> register(
        registry: ResourceKey<out Registry<R>>,
        id: ResourceLocation,
        factory: () -> T,
    ): RegisteredEntry<T>

    /**
     * 在這之前交出的所有項目都寫入登記表之後執行 [action]。
     *
     * [action] 在遊戲主執行緒上執行，不會與其他 mod 的初始化同時進行，可以寫入原版共用的表。
     *
     * @param action 需要讀取已登記物件或寫入原版共用表的工作。
     */
    fun afterRegistration(action: () -> Unit)

    /**
     * 建立創造模式物品分頁的建構器；分頁在物品欄中的位置由載入器安排。
     *
     * @return 尚未設定內容的分頁建構器。
     */
    fun creativeModeTabBuilder(): CreativeModeTab.Builder
}
