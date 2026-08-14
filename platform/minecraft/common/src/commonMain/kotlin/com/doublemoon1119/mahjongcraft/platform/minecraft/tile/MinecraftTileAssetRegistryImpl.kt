package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId

/**
 * [MinecraftTileAssetRegistry] 的預設記憶體實作。
 *
 * 建構時不預先加入任何映射；內建與第三方 asset key 使用相同的 [register] 流程，並由外層組裝完成後
 * 呼叫 [freeze]。此類別不依賴 Koin，runtime single 由外層 DI module 管理。
 */
class MinecraftTileAssetRegistryImpl : MinecraftTileAssetRegistry {
    /** 依穩定 ID 保存 asset key。 */
    private val assetKeysById = mutableMapOf<TileTypeId, String>()

    override var isFrozen: Boolean = false
        private set

    override fun register(typeId: TileTypeId, assetKey: String) {
        check(!isFrozen) { "Minecraft tile asset registry is frozen" }
        require(typeId !in assetKeysById) { "Tile asset key already registered for ID: $typeId" }
        assetKeysById[typeId] = assetKey
    }

    override fun freeze() {
        isFrozen = true
    }

    override fun find(typeId: TileTypeId): String? = assetKeysById[typeId]
}
