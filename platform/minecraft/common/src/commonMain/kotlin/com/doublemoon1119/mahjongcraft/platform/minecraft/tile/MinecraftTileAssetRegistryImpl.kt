package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId

/**
 * [MinecraftTileAssetRegistry] 的預設記憶體實作。
 *
 * 建構時不預先加入任何映射；內建與第三方 asset key 使用相同的 [register] 流程，並由外層組裝完成後
 * 呼叫 [freeze]。runtime single 由 Koin 管理。
 */
class MinecraftTileAssetRegistryImpl : MinecraftTileAssetRegistry {
    /** 依穩定 ID 保存 asset key。 */
    private val assetKeysById = mutableMapOf<TileTypeId, String>()

    /** 依 asset key 保存完整資源描述。 */
    private val resourcesByAssetKey = mutableMapOf<String, MinecraftTileResources>()

    /** 依外觀 ID 保存牌組外觀。 */
    private val appearancesById = mutableMapOf<String, MinecraftTileSetAppearanceDefinition>()

    /** 依規則模組 ID 保存牌組外觀綁定。 */
    private val appearanceIdsByRuleModuleId = mutableMapOf<String, String>()

    override val registrationKeys: Set<String> get() = assetKeysById.values.toSet()

    override val appearanceRegistrationKeys: Set<String> get() = appearancesById.keys.toSet()

    override val ruleAppearanceBindingKeys: Set<String> get() = appearanceIdsByRuleModuleId.keys.toSet()

    override val registeredAppearances: List<MinecraftTileSetAppearanceDefinition>
        get() = appearancesById.values.toList()

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

    override fun register(definition: MinecraftTileSetAppearanceDefinition) {
        check(!isFrozen) { "Minecraft tile asset registry is frozen" }
        require(definition.id !in appearancesById) {
            "Tile set appearance already registered: ${definition.id}"
        }
        appearancesById[definition.id] = definition
    }

    override fun register(definition: MinecraftRuleTileAppearanceDefinition) {
        check(!isFrozen) { "Minecraft tile asset registry is frozen" }
        require(definition.ruleModuleId !in appearanceIdsByRuleModuleId) {
            "Rule tile appearance already registered: ${definition.ruleModuleId}"
        }
        appearanceIdsByRuleModuleId[definition.ruleModuleId] = definition.appearanceId
    }

    override fun freeze() {
        appearanceIdsByRuleModuleId.forEach { (ruleModuleId, appearanceId) ->
            require(appearanceId in appearancesById) {
                "Rule tile appearance references an unknown appearance: $ruleModuleId -> $appearanceId"
            }
        }
        isFrozen = true
    }

    override fun find(typeId: TileTypeId): String? = assetKeysById[typeId]

    override fun findResources(assetKey: String): MinecraftTileResources? = resourcesByAssetKey[assetKey]

    override fun findAppearance(appearanceId: String): MinecraftTileSetAppearanceDefinition? = appearancesById[appearanceId]

    override fun findAppearanceForRule(ruleModuleId: String): MinecraftTileSetAppearanceDefinition? = appearancesById[appearanceIdsByRuleModuleId[ruleModuleId]]
        ?: appearancesById[BuiltInMinecraftTileAppearanceIds.DEFAULT]
        ?: BuiltInMinecraftTileAppearances.DEFAULT

    override fun isRegisteredAssetKey(assetKey: String): Boolean = assetKeysById.containsValue(assetKey)

    override val registeredAssetKeys: Set<String> get() = assetKeysById.values.toSet()
}
