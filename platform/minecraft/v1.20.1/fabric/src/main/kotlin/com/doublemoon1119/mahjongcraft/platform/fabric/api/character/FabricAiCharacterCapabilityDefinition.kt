package com.doublemoon1119.mahjongcraft.platform.fabric.api.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/**
 * 讓角色外觀的 worldCapabilityId 對應到原生角色提供者。
 *
 * @property id 能力的完整 namespace:path 識別碼。
 * @property provider 取得原生角色的工廠。
 */
class FabricAiCharacterCapabilityDefinition(
    val id: String,
    val provider: FabricAiCharacterProvider,
) {
    init {
        MinecraftResourceIds.requireValid(id) { "Character capability must be namespaced: $id" }
    }
}
