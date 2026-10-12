package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import org.koin.core.annotation.Single
/** [TablePropDescriberRegistry] 的記憶體實作。 */
@Single(binds = [TablePropDescriberRegistry::class])
class TablePropDescriberRegistryImpl : TablePropDescriberRegistry {
    /** 依規則模組識別碼索引的描述。 */
    private val describers = mutableMapOf<String, TablePropDescriber>()

    override val registrationKeys: Set<String> get() = describers.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(ruleModuleId: String, describer: TablePropDescriber) {
        check(!isFrozen) { "Table prop describer registry is frozen" }
        require(ruleModuleId.isNotBlank()) { "Rule module ID must not be blank" }
        require(describers.putIfAbsent(ruleModuleId, describer) == null) { "Duplicate table prop describer: $ruleModuleId" }
    }

    override fun find(ruleModuleId: String): TablePropDescriber? = describers[ruleModuleId]

    override fun freeze() {
        isFrozen = true
    }
}
