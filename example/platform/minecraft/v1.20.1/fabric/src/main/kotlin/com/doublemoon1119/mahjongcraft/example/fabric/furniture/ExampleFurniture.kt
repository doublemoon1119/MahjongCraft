package com.doublemoon1119.mahjongcraft.example.fabric.furniture

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.seating.ExampleFurnitureSeatProviders
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.RegisteredSeatProvider
import net.minecraft.entity.Entity
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier

/** 範例家具的原生註冊與座位 provider 組裝入口。 */
object ExampleFurniture {
    /** 範例椅子物品；完成 [register] 後可用。 */
    val chairItem: Item
        get() = Registries.ITEM.get(Identifier(ExampleFurnitureIds.CHAIR))

    /** 範例坐墊物品；完成 [register] 後可用。 */
    val cushionItem: Item
        get() = Registries.ITEM.get(Identifier(ExampleFurnitureIds.CUSHION))

    /** 註冊範例家具的方塊、物品與 entity type。 */
    fun register() {
        ExampleFurnitureEntities.register()
        ExampleFurnitureBlocks.register()
        ExampleFurnitureItems.register()
    }

    /** 建立不接觸原生世界的座位 provider 描述，供 extension bootstrap 登記。 */
    fun seatProviders(): List<RegisteredSeatProvider<ServerWorld, Entity>> = ExampleFurnitureSeatProviders.definitions()
}
