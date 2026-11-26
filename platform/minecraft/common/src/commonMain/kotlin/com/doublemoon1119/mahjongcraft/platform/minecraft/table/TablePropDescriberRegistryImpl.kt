package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
/** [TablePropDescriberRegistry] 的記憶體實作。 */
class TablePropDescriberRegistryImpl : TablePropDescriberRegistry {
    /** 依規則模組識別碼索引的描述。 */
    private val describers = mutableMapOf<String, TablePropDescriber>()

    override val registrationKeys: Set<String> get() = describers.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: TablePropDescriberDefinition) {
        val ruleModuleId = definition.ruleModuleId
        val describer = definition.describer
        check(!isFrozen) { "Table prop describer registry is frozen" }
        NamespacedId.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        require(describers.putIfAbsent(ruleModuleId, describer) == null) { "Duplicate table prop describer: $ruleModuleId" }
    }

    override fun find(ruleModuleId: String): TablePropDescriber? = describers[ruleModuleId]

    override fun freeze() {
        isFrozen = true
    }
}
