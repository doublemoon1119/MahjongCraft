package com.doublemoon1119.mahjongcraft.example.fabric.registry

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurniture
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurnitureIds
import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleCharacterIds
import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleFurnitureMessageKeys
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

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
            .title(Component.translatable(ExampleFurnitureMessageKeys.ITEM_GROUP))
            .displayItems { _, entries -> itemIds().forEach { entries.accept(BuiltInRegistries.ITEM.get(ResourceLocation(it))) } }
            .build()
        Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            ResourceLocation(ExampleFurnitureIds.ITEM_GROUP),
            group,
        )
    }
}
