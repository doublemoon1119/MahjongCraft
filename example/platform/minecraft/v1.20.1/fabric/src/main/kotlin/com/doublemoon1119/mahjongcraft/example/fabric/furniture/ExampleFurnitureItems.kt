package com.doublemoon1119.mahjongcraft.example.fabric.furniture

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.item.ExampleCushionItem
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

/** 範例家具物品的集中註冊點。 */
object ExampleFurnitureItems {
    /** 放置可保存坐墊的物品；由 [register] 初始化。 */
    lateinit var cushion: Item
        private set

    /** 註冊範例坐墊物品。 */
    fun register() {
        cushion = Registry.register(
            Registries.ITEM,
            Identifier(ExampleFurnitureIds.CUSHION),
            ExampleCushionItem(Item.Settings().maxCount(16)),
        )
    }
}
