package com.doublemoon1119.mahjongcraft.platform.minecraft.preparation

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import org.koin.core.annotation.Single

/** [RoundPreparationDisplayNameRegistry] 的記憶體實作。 */
@Single(binds = [RoundPreparationDisplayNameRegistry::class])
class RoundPreparationDisplayNameRegistryImpl : RoundPreparationDisplayNameRegistry {
    /** 依完整 ID 索引的 translation key。 */
    private val translations = mutableMapOf<String, String>()

    override val registrationKeys: Set<String> get() = translations.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(id: String, translationKey: String) {
        check(!isFrozen) { "Round-preparation display-name registry is frozen" }
        NamespacedId.requireValid(id) {
            "Round-preparation display ID must be namespaced: $id"
        }
        require(translationKey.isNotBlank()) { "Translation key must not be blank" }
        require(translations.putIfAbsent(id, translationKey) == null) {
            "Duplicate round-preparation display ID: $id"
        }
    }

    override fun find(id: String): String? = translations[id]

    override fun freeze() {
        isFrozen = true
    }
}
