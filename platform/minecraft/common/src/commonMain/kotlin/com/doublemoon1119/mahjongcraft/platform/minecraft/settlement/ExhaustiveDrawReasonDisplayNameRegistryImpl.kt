package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import org.koin.core.annotation.Single

/** [ExhaustiveDrawReasonDisplayNameRegistry] 的記憶體實作。 */
@Single(binds = [ExhaustiveDrawReasonDisplayNameRegistry::class])
class ExhaustiveDrawReasonDisplayNameRegistryImpl : ExhaustiveDrawReasonDisplayNameRegistry {
    /** 流局原因 ID 對應的本地化名稱。 */
    private val translations = mutableMapOf<String, String>()

    /** 流局原因 ID 對應的玩家結算身分用語。 */
    private val statusLabels = mutableMapOf<String, ExhaustiveDrawSettlementStatusLabels>()
    override var isFrozen: Boolean = false
        private set
    override val reasonIds: Set<String> get() = translations.keys
    override val registrationKeys: Set<String> get() = translations.keys + statusLabels.keys

    override fun register(reasonId: String, translationKey: String) {
        check(!isFrozen) { "Exhaustive-draw reason display-name registry is frozen" }
        NamespacedId.requireValid(reasonId) { "Exhaustive-draw reason ID must be namespaced: $reasonId" }
        require(translationKey.isNotBlank()) { "Translation key must not be blank" }
        require(translations.putIfAbsent(reasonId, translationKey) == null) { "Duplicate exhaustive-draw reason: $reasonId" }
    }

    override fun find(reasonId: String): String? = translations[reasonId]

    override fun registerSettlementStatusLabels(
        reasonId: String,
        labels: ExhaustiveDrawSettlementStatusLabels,
    ) {
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
