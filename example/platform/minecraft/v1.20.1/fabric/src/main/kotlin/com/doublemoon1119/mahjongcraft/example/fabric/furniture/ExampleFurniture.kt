package com.doublemoon1119.mahjongcraft.example.fabric.furniture

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleFurnitureSeatProviders
import com.doublemoon1119.mahjongcraft.example.fabric.furniture.table.ExampleTables
import com.doublemoon1119.mahjongcraft.example.minecraft.furniture.ExampleFurnitureIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item

/** 範例家具的原生註冊與座位 provider 組裝入口。 */
object ExampleFurniture {
    /** 範例椅子物品；完成 [register] 後可用。 */
    val chairItem: Item
        get() = BuiltInRegistries.ITEM.get(ResourceLocation(ExampleFurnitureIds.CHAIR))

    /** 範例坐墊物品；完成 [register] 後可用。 */
    val cushionItem: Item
        get() = BuiltInRegistries.ITEM.get(ResourceLocation(ExampleFurnitureIds.CUSHION))

    /** 註冊範例家具的方塊、物品與 entity type。 */
    fun register() {
        ExampleFurnitureEntities.register()
        ExampleFurnitureBlocks.register()
        ExampleFurnitureItems.register()
        ExampleTables.register()
    }

    /** 建立不接觸原生世界的座位 provider 描述，供 extension bootstrap 登記。 */
    fun seatProviders(): List<RegisteredSeatProvider<ServerLevel, Entity>> = ExampleFurnitureSeatProviders.definitions()
}
