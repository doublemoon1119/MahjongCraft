package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** [TableTileLayoutProfileRegistry] 的記憶體實作。 */
class TableTileLayoutProfileRegistryImpl : TableTileLayoutProfileRegistry {
    /** 依規則模組識別碼索引的配置。 */
    private val profiles = mutableMapOf<String, TableTileLayoutProfile>()

    override val registrationKeys: Set<String> get() = profiles.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: TableTileLayoutProfileDefinition) {
        val ruleModuleId = definition.ruleModuleId
        check(!isFrozen) { "Table tile layout profile registry is frozen" }
        NamespacedId.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        require(profiles.putIfAbsent(ruleModuleId, definition.profile) == null) { "Duplicate table tile layout profile: $ruleModuleId" }
    }

    override fun profileFor(ruleModuleId: String): TableTileLayoutProfile = profiles[ruleModuleId] ?: TableTileLayoutProfile.STANDARD

    override fun freeze() {
        isFrozen = true
    }
}
