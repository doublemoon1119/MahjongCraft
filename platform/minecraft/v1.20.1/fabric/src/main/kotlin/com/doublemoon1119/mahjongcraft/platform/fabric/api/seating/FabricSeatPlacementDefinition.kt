package com.doublemoon1119.mahjongcraft.platform.fabric.api.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/**
 * 一項自行攜帶識別碼的座位物品放置能力。
 *
 * @property id 完整 namespace:path 識別碼。
 * @property provider 物品辨識與放置實作。
 * @property priority 較大的值先選，同值依完整識別碼排序；一次互動只採用一項能力。
 */
class FabricSeatPlacementDefinition(
    val id: String,
    val provider: FabricSeatPlacementProvider,
    val priority: Int = 0,
) {
    init {
        MinecraftResourceIds.requireValid(id) { "Seat placement ID must be namespaced: $id" }
    }
}
