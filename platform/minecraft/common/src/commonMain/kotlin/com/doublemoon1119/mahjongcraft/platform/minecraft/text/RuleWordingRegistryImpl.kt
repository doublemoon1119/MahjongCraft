package com.doublemoon1119.mahjongcraft.platform.minecraft.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/** [RuleWordingRegistry] 的記憶體實作。 */
class RuleWordingRegistryImpl : RuleWordingRegistry {
    /** 依文字 ID 索引的中立用語。 */
    private val defaults = mutableMapOf<String, String>()

    /** 依規則模組與文字 ID 索引的規則說法。 */
    private val byRuleModule = mutableMapOf<Pair<String, String>, String>()

    override val registrationKeys: Set<String>
        get() = defaults.keys.mapTo(mutableSetOf()) { wordingId -> "$DEFAULT_KEY/$wordingId" } +
            byRuleModule.keys.map { (ruleModuleId, wordingId) -> "$ruleModuleId/$wordingId" }

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: RuleWordingDefaultDefinition) {
        val wordingId = definition.wordingId
        check(!isFrozen) { "Rule wording registry is frozen" }
        MinecraftResourceIds.requireValid(wordingId) { "Rule wording ID must be namespaced: $wordingId" }
        require(definition.translationKey.isNotBlank()) { "Rule wording translation key must not be blank: $wordingId" }
        require(defaults.putIfAbsent(wordingId, definition.translationKey) == null) { "Default rule wording already registered: $wordingId" }
    }

    override fun register(definition: RuleWordingDefinition) {
        val ruleModuleId = definition.ruleModuleId
        val wordingId = definition.wordingId
        check(!isFrozen) { "Rule wording registry is frozen" }
        MinecraftResourceIds.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        MinecraftResourceIds.requireValid(wordingId) { "Rule wording ID must be namespaced: $wordingId" }
        require(definition.translationKey.isNotBlank()) { "Rule wording translation key must not be blank: $ruleModuleId/$wordingId" }
        require(byRuleModule.putIfAbsent(ruleModuleId to wordingId, definition.translationKey) == null) {
            "Rule wording already registered: $ruleModuleId/$wordingId"
        }
    }

    override fun find(ruleModuleId: String?, wordingId: String): String? = ruleModuleId?.let { byRuleModule[it to wordingId] } ?: defaults[wordingId]

    override fun freeze() {
        isFrozen = true
    }

    private companion object {
        /** 中立用語在登記快照中的前綴。 */
        const val DEFAULT_KEY: String = "default"
    }
}
