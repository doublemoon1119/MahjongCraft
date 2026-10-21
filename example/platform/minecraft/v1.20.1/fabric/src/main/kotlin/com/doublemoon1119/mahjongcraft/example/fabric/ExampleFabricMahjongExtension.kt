package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.ExampleFurniture
import com.doublemoon1119.mahjongcraft.example.minecraft.ExampleMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatProviderRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricMahjongExtension

/** 由 Fabric entrypoint 發現，將同一個範例交給核心、Minecraft 與原生擴充登記流程。 */
class ExampleFabricMahjongExtension :
    ExampleMinecraftMahjongExtension(),
    FabricMahjongExtension {
    /**
     * 登記椅子與坐墊的解析能力；此時不讀取原生家具或世界。
     *
     * @param registry 本體提供的座位 registry。
     */
    override fun registerSeatProviders(registry: FabricSeatProviderRegistry) {
        ExampleFurniture.seatProviders().forEach(registry::register)
    }
}
