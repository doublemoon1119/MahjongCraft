package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import org.koin.core.annotation.Single

/**
 * [MinecraftTileAssetRegistry] 的預設記憶體實作。
 *
 * 建構時不預先加入任何映射；內建與第三方 asset key 使用相同的 [register] 流程，並由外層組裝完成後
 * 呼叫 [freeze]。runtime single 由 Koin component scan 管理。
 */
@Single(binds = [MinecraftTileAssetRegistry::class])
class MinecraftTileAssetRegistryImpl : MinecraftTileAssetRegistry {
    /** 依穩定 ID 保存 asset key。 */
    private val assetKeysById = mutableMapOf<TileTypeId, String>()

    /** 依 asset key 保存完整資源描述。 */
    private val resourcesByAssetKey = mutableMapOf<String, MinecraftTileResources>()

    override val registrationKeys: Set<String> get() = assetKeysById.values.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: MinecraftTileAssetDefinition) {
        val typeId = definition.typeId
        val assetKey = definition.assetKey
        check(!isFrozen) { "Minecraft tile asset registry is frozen" }
        require(assetKey !in STANDARD_TILE_ASSET_KEYS && assetKey != UNKNOWN_TILE_ASSET_KEY) {
            "Tile asset key is reserved for a built-in tile: $assetKey"
        }
        require(typeId !in assetKeysById) { "Tile asset key already registered for ID: $typeId" }
        require(assetKey !in assetKeysById.values) {
            "Asset key \"$assetKey\" is already registered to a different tile type"
        }
        assetKeysById[typeId] = assetKey
        resourcesByAssetKey[assetKey] = definition.resources
    }

    override fun freeze() {
        isFrozen = true
    }

    override fun find(typeId: TileTypeId): String? = assetKeysById[typeId]

    override fun findResources(assetKey: String): MinecraftTileResources? = resourcesByAssetKey[assetKey]

    override fun isRegisteredAssetKey(assetKey: String): Boolean = assetKeysById.containsValue(assetKey)

    override val registeredAssetKeys: Set<String> get() = assetKeysById.values.toSet()
}
