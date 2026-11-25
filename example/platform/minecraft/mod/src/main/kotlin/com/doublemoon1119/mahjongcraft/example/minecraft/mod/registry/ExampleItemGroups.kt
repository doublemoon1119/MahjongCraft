package com.doublemoon1119.mahjongcraft.example.minecraft.mod.registry

import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleCharacterIds
import com.doublemoon1119.mahjongcraft.example.minecraft.furniture.ExampleFurnitureIds
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.furniture.ExampleFurniture
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.vanilla.resourceId
import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleFurnitureMessageKeys
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
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

    /**
     * 登記以椅子為圖示的共用分頁。分頁的內容在開啟物品欄時才讀取，這時範例物品都已登記。
     *
     * @param registrar 載入器的登記方式。
     */
    fun register(registrar: ExampleRegistrar) {
        registrar.register(
            registry = Registries.CREATIVE_MODE_TAB,
            id = resourceId(ExampleFurnitureIds.ITEM_GROUP),
            factory = {
                registrar.creativeModeTabBuilder()
                    .icon { ItemStack(ExampleFurniture.chairItem) }
                    .title(Component.translatable(ExampleFurnitureMessageKeys.ITEM_GROUP))
                    .displayItems { _, entries -> itemIds().forEach { entries.accept(BuiltInRegistries.ITEM.get(resourceId(it))) } }
                    .build()
            },
        )
    }
}
