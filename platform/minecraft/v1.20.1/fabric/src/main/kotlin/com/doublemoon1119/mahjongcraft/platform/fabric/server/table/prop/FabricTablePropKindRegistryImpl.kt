package com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds
import org.koin.core.annotation.Single

/** [FabricTablePropKindRegistry] 的記憶體實作。 */
@Single(binds = [FabricTablePropKindRegistry::class])
class FabricTablePropKindRegistryImpl : FabricTablePropKindRegistry {
    /** 依識別碼索引、保留登記順序的種類。 */
    private val kindsById = linkedMapOf<String, FabricTablePropKind>()

    override val registrationKeys: Set<String> get() = kindsById.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override val kinds: List<FabricTablePropKind> get() = kindsById.values.toList()

    override fun register(kind: FabricTablePropKind) {
        check(!isFrozen) { "Table prop kind registry is frozen" }
        MinecraftResourceIds.requireValid(kind.id) { "Table prop kind must be namespaced: ${kind.id}" }
        require(kindsById.putIfAbsent(kind.id, kind) == null) { "Duplicate table prop kind: ${kind.id}" }
    }

    override fun find(kindId: String): FabricTablePropKind? = kindsById[kindId]

    override fun freeze() {
        isFrozen = true
    }
}
