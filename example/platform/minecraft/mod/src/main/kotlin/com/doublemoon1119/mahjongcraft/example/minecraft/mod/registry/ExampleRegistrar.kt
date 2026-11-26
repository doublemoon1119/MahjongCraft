package com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab

/**
 * 範例內容寫入登記表的方式。每個載入器的範例模組各自提供。
 *
 * - 範例要登記的內容只在共用程式中列一次。
 * - 有的載入器在呼叫當下就寫入登記表，有的要等到各登記表輪到時才寫入。
 *   因此登記時交出建立物件的函式，寫入後再以 [register] 的 `onRegistered` 交回物件。
 * - 這個介面屬於範例，不是 MahjongCraft 的 API，只為了讓兩個載入器的範例共用同一份內容清單。
 * - 只支援一個載入器的 mod 直接用該載入器平常的方式登記即可，例如 Fabric 的 `Registry.register` 或 Forge 的 `DeferredRegister`。
 */
interface ExampleRegistrar {
    /**
     * 登記一個物件。
     *
     * @param T 登記的物件型別。
     * @param registry 要寫入的登記表。
     * @param id 物件在登記表中的 ID。
     * @param factory 建立物件的函式。載入器寫入登記表時呼叫一次。
     * @param onRegistered 物件寫入後收到它，用來保存供其他程式使用。
     */
    fun <T : Any> register(registry: ResourceKey<out Registry<in T>>, id: ResourceLocation, factory: () -> T, onRegistered: (T) -> Unit = {})

    /**
     * 建立創造模式物品分頁的建構器。分頁在物品欄中的位置由載入器安排。
     *
     * @return 尚未設定內容的分頁建構器。
     */
    fun creativeModeTabBuilder(): CreativeModeTab.Builder
}
