package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** [ExhaustiveDrawReasonDisplayNameRegistry] 的記憶體實作。 */
class ExhaustiveDrawReasonDisplayNameRegistryImpl : ExhaustiveDrawReasonDisplayNameRegistry {
    /** 流局原因 ID 對應的本地化名稱。 */
    private val translations = mutableMapOf<String, String>()

    /** 流局原因 ID 對應的玩家結算身分用語。 */
    private val statusLabels = mutableMapOf<String, ExhaustiveDrawSettlementStatusLabels>()
    override var isFrozen: Boolean = false
        private set

    override val reasonIds: Set<String> get() = translations.keys.toSet()

    override val registrationKeys: Set<String> get() = (translations.keys + statusLabels.keys).toSet()

    override fun register(definition: ExhaustiveDrawReasonDisplayNameDefinition) {
        val reasonId = definition.reasonId
        val translationKey = definition.translationKey
        check(!isFrozen) { "Exhaustive-draw reason display-name registry is frozen" }
        NamespacedId.requireValid(reasonId) { "Exhaustive-draw reason ID must be namespaced: $reasonId" }
        require(translationKey.isNotBlank()) { "Translation key must not be blank" }
        require(translations.putIfAbsent(reasonId, translationKey) == null) { "Duplicate exhaustive-draw reason: $reasonId" }
    }

    override fun find(reasonId: String): String? = translations[reasonId]

    override fun register(definition: ExhaustiveDrawSettlementStatusLabelsDefinition) {
        val reasonId = definition.reasonId
        val labels = definition.labels
        check(!isFrozen) { "Exhaustive-draw reason display-name registry is frozen" }
        NamespacedId.requireValid(reasonId) { "Exhaustive-draw reason ID must be namespaced: $reasonId" }
        require(statusLabels.putIfAbsent(reasonId, labels) == null) {
            "Duplicate exhaustive-draw settlement status labels: $reasonId"
        }
    }

    override fun findSettlementStatusLabels(reasonId: String): ExhaustiveDrawSettlementStatusLabels? = statusLabels[reasonId]

    override fun freeze() {
        isFrozen = true
    }
}
