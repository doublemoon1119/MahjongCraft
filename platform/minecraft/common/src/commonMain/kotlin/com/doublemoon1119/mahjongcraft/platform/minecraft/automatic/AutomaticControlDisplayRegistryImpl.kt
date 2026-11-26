package com.doublemoon1119.mahjongcraft.platform.minecraft.automatic

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/** [AutomaticControlDisplayRegistry] 的記憶體實作。 */
class AutomaticControlDisplayRegistryImpl : AutomaticControlDisplayRegistry {
    /** 依自動操作識別碼索引的顯示資料。 */
    private val displays = mutableMapOf<String, AutomaticControlDisplay>()

    /** 依規則模組與自動操作識別碼索引的規則專屬顯示資料。 */
    private val byRuleModule = mutableMapOf<Pair<String, String>, AutomaticControlDisplay>()

    override val registrationKeys: Set<String>
        get() = displays.keys + byRuleModule.keys.map { (ruleModuleId, controlId) -> "$ruleModuleId/$controlId" }

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: AutomaticControlDisplayDefinition) {
        val controlId = definition.controlId
        val display = definition.display
        check(!isFrozen) { "Automatic control display registry is frozen" }
        MinecraftResourceIds.requireValid(controlId) { "Automatic control ID must be namespaced: $controlId" }
        require(displays.putIfAbsent(controlId, display) == null) {
            "Automatic control display already registered: $controlId"
        }
    }

    override fun register(definition: AutomaticControlRuleDisplayDefinition) {
        val ruleModuleId = definition.ruleModuleId
        val controlId = definition.controlId
        check(!isFrozen) { "Automatic control display registry is frozen" }
        MinecraftResourceIds.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        MinecraftResourceIds.requireValid(controlId) { "Automatic control ID must be namespaced: $controlId" }
        require(byRuleModule.putIfAbsent(ruleModuleId to controlId, definition.display) == null) {
            "Automatic control display already registered: $ruleModuleId/$controlId"
        }
    }

    override fun find(ruleModuleId: String?, controlId: String): AutomaticControlDisplay? = ruleModuleId?.let { byRuleModule[it to controlId] } ?: displays[controlId]

    override fun freeze() {
        isFrozen = true
    }
}
