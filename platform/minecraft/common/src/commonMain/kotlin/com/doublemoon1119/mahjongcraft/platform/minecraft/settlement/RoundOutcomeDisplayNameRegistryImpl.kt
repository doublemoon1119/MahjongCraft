package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import org.koin.core.annotation.Single

/** [RoundOutcomeDisplayNameRegistry] 的記憶體實作。 */
@Single(binds = [RoundOutcomeDisplayNameRegistry::class])
class RoundOutcomeDisplayNameRegistryImpl : RoundOutcomeDisplayNameRegistry {
    /** 特殊本局結果 ID 對應的本地化名稱。 */
    private val translations = mutableMapOf<String, String>()

    override var isFrozen: Boolean = false
        private set

    override val registrationKeys: Set<String> get() = translations.keys.toSet()

    override fun register(definition: RoundOutcomeDisplayNameDefinition) {
        val outcomeId = definition.outcomeId
        val translationKey = definition.translationKey
        check(!isFrozen) { "Round outcome display-name registry is frozen" }
        NamespacedId.requireValid(outcomeId) { "Round outcome ID must be namespaced: $outcomeId" }
        require(translationKey.isNotBlank()) { "Translation key must not be blank" }
        require(translations.putIfAbsent(outcomeId, translationKey) == null) { "Duplicate round outcome display name: $outcomeId" }
    }

    override fun find(outcomeId: String): String? = translations[outcomeId]

    override fun freeze() {
        isFrozen = true
    }
}
