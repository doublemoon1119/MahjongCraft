package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** 將規則特殊本局結果 ID（例如日麻的流局滿貫）映射至 Minecraft translation key。 */
interface RoundOutcomeDisplayNameRegistry {
    /** 目前已登記結果 ID 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 登記結果 ID 的本地化名稱；重複 ID 會失敗。 */
    fun register(
        outcomeId: String,
        translationKey: String,
    )

    /** 查詢本地化名稱；未登記時回傳 null。 */
    fun find(outcomeId: String): String?

    /** 凍結 registry。 */
    fun freeze()
}

/** [RoundOutcomeDisplayNameRegistry] 的記憶體實作。 */
class RoundOutcomeDisplayNameRegistryImpl : RoundOutcomeDisplayNameRegistry {
    private val translations = mutableMapOf<String, String>()
    override var isFrozen: Boolean = false
        private set
    override val registrationKeys: Set<String> get() = translations.keys.toSet()

    override fun register(
        outcomeId: String,
        translationKey: String,
    ) {
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
