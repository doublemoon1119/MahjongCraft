package com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropKindDefinition

/** [FabricTablePropKindRegistry] 的記憶體實作。 */
class FabricTablePropKindRegistryImpl : FabricTablePropKindRegistry {
    /** 依識別碼索引、保留登記順序的種類。 */
    private val kindsById = linkedMapOf<String, FabricTablePropKind>()

    /** 登記時固定的共用種類資訊，避免後續 getter 回傳不同尺寸。 */
    private val definitionsById = linkedMapOf<String, TablePropKindDefinition>()

    override val definitions: Map<String, TablePropKindDefinition> get() = definitionsById.toMap()

    override val registrationKeys: Set<String> get() = kindsById.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override val kinds: List<FabricTablePropKind> get() = kindsById.values.toList()

    override fun register(kind: FabricTablePropKind) {
        check(!isFrozen) { "Table prop kind registry is frozen" }
        MinecraftResourceIds.requireValid(kind.id) { "Table prop kind must be namespaced: ${kind.id}" }
        val definition = kind.definition
        require(kind.id == definition.id) { "Table prop kind ID differs from its definition: ${kind.id}" }
        require(kindsById.putIfAbsent(definition.id, kind) == null) { "Duplicate table prop kind: ${definition.id}" }
        definitionsById[definition.id] = definition
    }

    override fun find(kindId: String): FabricTablePropKind? = kindsById[kindId]

    override fun freeze() {
        isFrozen = true
    }
}
