package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** [HistoryTileSetAsideDisplayRegistry] 的記憶體實作。 */
class HistoryTileSetAsideDisplayRegistryImpl : HistoryTileSetAsideDisplayRegistry {
    /** 依牌區識別碼保存標題翻譯 key。 */
    private val areaNames = mutableMapOf<String, String>()

    /** 依規則模組識別碼保存換牌用語翻譯 key。 */
    private val replacementNames = mutableMapOf<String, String>()

    override var isFrozen: Boolean = false
        private set

    override val registrationKeys: Set<String>
        get() = areaNames.keys.map { "area:$it" }.toSet() + replacementNames.keys.map { "replacement:$it" }

    override fun register(definition: HistoryTileAreaDisplayDefinition) {
        check(!isFrozen) { "History tile set-aside display registry is frozen" }
        NamespacedId.requireValid(definition.areaId) { "Tile area ID must be namespaced: ${definition.areaId}" }
        require(definition.translationKey.isNotBlank()) { "Translation key must not be blank" }
        require(areaNames.putIfAbsent(definition.areaId, definition.translationKey) == null) { "Duplicate tile area display: ${definition.areaId}" }
    }

    override fun register(definition: HistoryTileReplacementDisplayDefinition) {
        check(!isFrozen) { "History tile set-aside display registry is frozen" }
        NamespacedId.requireValid(definition.ruleModuleId) { "Rule module ID must be namespaced: ${definition.ruleModuleId}" }
        require(definition.translationKey.isNotBlank()) { "Translation key must not be blank" }
        require(replacementNames.putIfAbsent(definition.ruleModuleId, definition.translationKey) == null) {
            "Duplicate tile replacement display: ${definition.ruleModuleId}"
        }
    }

    override fun findAreaName(areaId: String): String? = areaNames[areaId]

    override fun findReplacementName(ruleModuleId: String?): String? = ruleModuleId?.let(replacementNames::get)

    override fun freeze() {
        isFrozen = true
    }
}
