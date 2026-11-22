package com.doublemoon1119.mahjongcraft.example.fabric.furniture

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.item.ExampleCushionItem
import com.doublemoon1119.mahjongcraft.example.minecraft.furniture.ExampleFurnitureIds
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item

/** 範例家具物品的集中註冊點。 */
object ExampleFurnitureItems {
    /** 放置可保存坐墊的物品；由 [register] 初始化。 */
    lateinit var cushion: Item
        private set

    /** 註冊範例坐墊物品。 */
    fun register() {
        cushion = Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation(ExampleFurnitureIds.CUSHION),
            ExampleCushionItem(Item.Properties().stacksTo(16)),
        )
    }
}
