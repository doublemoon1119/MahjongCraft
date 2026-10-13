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

    override val registrationKeys: Set<String> get() = assetKeysById.values.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: MinecraftTileAssetDefinition) {
        val typeId = definition.typeId
        val assetKey = definition.assetKey
        check(!isFrozen) { "Minecraft tile asset registry is frozen" }
        require(typeId !in assetKeysById) { "Tile asset key already registered for ID: $typeId" }
        require(assetKey !in assetKeysById.values) {
            "Asset key \"$assetKey\" is already registered to a different tile type"
        }
        assetKeysById[typeId] = assetKey
    }

    override fun freeze() {
        isFrozen = true
    }

    override fun find(typeId: TileTypeId): String? = assetKeysById[typeId]

    override fun isRegisteredAssetKey(assetKey: String): Boolean = assetKeysById.containsValue(assetKey)

    override val registeredAssetKeys: Set<String> get() = assetKeysById.values.toSet()
}
