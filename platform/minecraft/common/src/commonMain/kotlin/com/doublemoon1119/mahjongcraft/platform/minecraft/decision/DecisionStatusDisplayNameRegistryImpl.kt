package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/** [DecisionStatusDisplayNameRegistry] 的記憶體實作。 */
class DecisionStatusDisplayNameRegistryImpl : DecisionStatusDisplayNameRegistry {
    /** 預設登記的狀態翻譯鍵，以狀態 ID 為索引。 */
    private val defaults = mutableMapOf<String, String>()

    /** 規則模組專屬的狀態翻譯鍵，以規則模組 ID 與狀態 ID 組成索引。 */
    private val byRuleModule = mutableMapOf<Pair<String, String>, String>()

    override val registrationKeys: Set<String>
        get() = defaults.keys.mapTo(mutableSetOf()) { statusId -> "${DecisionStatusDisplayNameRegistry.DEFAULT_KEY}/$statusId" } +
            byRuleModule.keys.map { (ruleModuleId, statusId) -> "$ruleModuleId/$statusId" }

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: DecisionStatusDisplayNameDefaultDefinition) {
        val statusId = definition.statusId
        val translationKey = definition.translationKey
        check(!isFrozen) { "Decision status display-name registry is frozen" }
        MinecraftResourceIds.requireValid(statusId) { "Decision status ID must be namespaced: $statusId" }
        require(translationKey.isNotBlank()) { "Translation key must not be blank" }
        require(defaults.putIfAbsent(statusId, translationKey) == null) { "Default decision status display name already registered: $statusId" }
    }

    override fun register(definition: DecisionStatusDisplayNameDefinition) {
        val ruleModuleId = definition.ruleModuleId
        val statusId = definition.statusId
        val translationKey = definition.translationKey
        check(!isFrozen) { "Decision status display-name registry is frozen" }
        MinecraftResourceIds.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        MinecraftResourceIds.requireValid(statusId) { "Decision status ID must be namespaced: $statusId" }
        require(translationKey.isNotBlank()) { "Translation key must not be blank" }
        require(byRuleModule.putIfAbsent(ruleModuleId to statusId, translationKey) == null) { "Decision status display name already registered: $ruleModuleId/$statusId" }
    }

    override fun find(ruleModuleId: String?, statusId: String): String? = ruleModuleId?.let { byRuleModule[it to statusId] } ?: defaults[statusId]

    override fun freeze() {
        isFrozen = true
    }
}
