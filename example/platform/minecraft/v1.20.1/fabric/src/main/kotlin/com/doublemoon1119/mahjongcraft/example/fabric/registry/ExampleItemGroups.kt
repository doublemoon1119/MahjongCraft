package com.doublemoon1119.mahjongcraft.example.fabric.registry

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurniture
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureIds
import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleFurnitureMessageKeys
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/** 範例專屬的創造模式分頁，不更改本體的物品分類。 */
object ExampleItemGroups {
    /** 依取得順序列出椅子與坐墊，不包含臨時支撐或生怪蛋。 */
    internal fun furnitureItemIds(): List<String> = listOf(ExampleFurnitureIds.CHAIR, ExampleFurnitureIds.CUSHION)

    /** 原生家具註冊完成後，註冊以椅子為圖示的家具分頁。 */
    fun register() {
        val group = FabricItemGroup.builder()
            .icon { ItemStack(ExampleFurniture.chairItem) }
            .displayName(Text.translatable(ExampleFurnitureMessageKeys.ITEM_GROUP))
            .entries { _, entries -> furnitureItemIds().forEach { entries.add(Registries.ITEM.get(Identifier(it))) } }
            .build()
        Registry.register(
            Registries.ITEM_GROUP,
            Identifier(ExampleFurnitureIds.ITEM_GROUP),
            group,
        )
    }
}
