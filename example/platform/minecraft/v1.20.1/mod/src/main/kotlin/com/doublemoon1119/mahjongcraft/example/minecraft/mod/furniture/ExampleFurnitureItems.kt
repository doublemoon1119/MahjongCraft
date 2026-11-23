package com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture

import com.doublemoon1119.mahjongcraft.example.minecraft.furniture.ExampleFurnitureIds
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.item.ExampleCushionItem
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry.ExampleRegistrar
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item

/** 範例家具物品的集中註冊點。 */
object ExampleFurnitureItems {
    /** 放置可保存坐墊的物品；寫入登記表後可用。 */
    lateinit var cushion: Item
        private set

    /**
     * 登記範例坐墊物品。
     *
     * @param registrar 載入器的登記方式。
     */
    fun register(registrar: ExampleRegistrar) {
        registrar.register(
            registry = Registries.ITEM,
            id = ResourceLocation(ExampleFurnitureIds.CUSHION),
            factory = { ExampleCushionItem(Item.Properties().stacksTo(16)) },
            onRegistered = { cushion = it },
        )
    }
}
