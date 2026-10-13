package com.doublemoon1119.mahjongcraft.platform.minecraft.action

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds
import org.koin.core.annotation.Single

/** [GameActionVocabularyRegistry] 的記憶體實作。 */
@Single(binds = [GameActionVocabularyRegistry::class])
class GameActionVocabularyRegistryImpl : GameActionVocabularyRegistry {
    /** 依動作 ID 索引的中立預設。 */
    private val defaults = mutableMapOf<String, GameActionVocabulary>()

    /** 依規則模組與動作 ID 索引的規則專屬用語。 */
    private val byRuleModule = mutableMapOf<Pair<String, String>, GameActionVocabulary>()

    override val registrationKeys: Set<String>
        get() = defaults.keys.mapTo(mutableSetOf()) { actionId -> "${GameActionVocabularyRegistry.DEFAULT_KEY}/$actionId" } +
            byRuleModule.keys.map { (ruleModuleId, actionId) -> "$ruleModuleId/$actionId" }

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: GameActionVocabularyDefaultDefinition) {
        val actionId = definition.actionId
        val vocabulary = definition.vocabulary
        check(!isFrozen) { "Game action vocabulary registry is frozen" }
        MinecraftResourceIds.requireValid(actionId) { "Game action ID must be namespaced: $actionId" }
        require(defaults.putIfAbsent(actionId, vocabulary) == null) {
            "Default game action vocabulary already registered: $actionId"
        }
    }

    override fun register(definition: GameActionVocabularyDefinition) {
        val ruleModuleId = definition.ruleModuleId
        val actionId = definition.actionId
        val vocabulary = definition.vocabulary
        check(!isFrozen) { "Game action vocabulary registry is frozen" }
        MinecraftResourceIds.requireValid(ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        MinecraftResourceIds.requireValid(actionId) { "Game action ID must be namespaced: $actionId" }
        require(byRuleModule.putIfAbsent(ruleModuleId to actionId, vocabulary) == null) {
            "Game action vocabulary already registered: $ruleModuleId/$actionId"
        }
    }

    override fun find(ruleModuleId: String?, actionId: String): GameActionVocabulary? = ruleModuleId?.let { byRuleModule[it to actionId] } ?: defaults[actionId]

    override fun freeze() {
        isFrozen = true
    }
}
