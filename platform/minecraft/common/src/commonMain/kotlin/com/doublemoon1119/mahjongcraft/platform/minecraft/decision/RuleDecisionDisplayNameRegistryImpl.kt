package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** [RuleDecisionDisplayNameRegistry] 的記憶體實作。 */
class RuleDecisionDisplayNameRegistryImpl : RuleDecisionDisplayNameRegistry {
    /** 依完整 ID 索引的 translation key。 */
    private val translations = mutableMapOf<String, String>()

    override val registrationKeys: Set<String> get() = translations.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: RuleDecisionDisplayNameDefinition) {
        val id = definition.id
        val translationKey = definition.translationKey
        check(!isFrozen) { "Rule-decision display-name registry is frozen" }
        NamespacedId.requireValid(id) {
            "Rule-decision display ID must be namespaced: $id"
        }
        require(translationKey.isNotBlank()) { "Translation key must not be blank" }
        require(translations.putIfAbsent(id, translationKey) == null) {
            "Duplicate rule-decision display ID: $id"
        }
    }

    override fun find(id: String): String? = translations[id]

    override fun freeze() {
        isFrozen = true
    }
}
