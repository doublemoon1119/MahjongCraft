package com.doublemoon1119.mahjongcraft.example.fabric.registry

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurniture
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureIds
import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleCharacterIds
import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleFurnitureMessageKeys
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/** 範例專屬的創造模式分頁，不更改本體的物品分類。 */
object ExampleItemGroups {
    /** 列出範例家具與人偶建立物品，不提供暫時支撐或自有代理物品。 */
    internal fun itemIds(): List<String> = listOf(
        ExampleFurnitureIds.CHAIR,
        ExampleFurnitureIds.CUSHION,
        ExampleFurnitureIds.BENCH,
        ExampleCharacterIds.ACTOR,
        ExampleFurnitureIds.ONE_BLOCK_TABLE,
        ExampleFurnitureIds.LOW_TABLE,
        ExampleFurnitureIds.ROUND_TABLE,
    )

    /** 原生範例物品註冊完成後，建立以椅子為圖示的共用分頁。 */
    fun register() {
        val group = FabricItemGroup.builder()
            .icon { ItemStack(ExampleFurniture.chairItem) }
            .displayName(Text.translatable(ExampleFurnitureMessageKeys.ITEM_GROUP))
            .entries { _, entries -> itemIds().forEach { entries.add(Registries.ITEM.get(Identifier(it))) } }
            .build()
        Registry.register(
            Registries.ITEM_GROUP,
            Identifier(ExampleFurnitureIds.ITEM_GROUP),
            group,
        )
    }
}
