package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Single

/** [RoundInfoLineDisplayRegistry] 的記憶體實作。 */
@Single(binds = [RoundInfoLineDisplayRegistry::class])
class RoundInfoLineDisplayRegistryImpl : RoundInfoLineDisplayRegistry {
    /** 依局況顯示行 key 索引的翻譯資訊。 */
    private val displays = mutableMapOf<String, RoundInfoLineDisplay>()

    /** 規則模組 ID 對應的局況顯示行 provider。 */
    private val providers = mutableMapOf<String, RoundInfoLineProvider>()

    override val registrationKeys: Set<String>
        get() = displays.keys.mapTo(mutableSetOf()) { "line:$it" } + providers.keys.map { "provider:$it" }

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: RoundInfoLineDisplayDefinition) {
        val key = definition.key
        val display = definition.display
        check(!isFrozen) { "Round info line display registry is frozen" }
        require(key.isNotBlank()) { "Round info line key must not be blank" }
        require(displays.putIfAbsent(key, display) == null) { "Duplicate round info line display: $key" }
    }

    override fun register(definition: RoundInfoLineProviderDefinition) {
        val ruleModuleId = definition.ruleModuleId
        val provider = definition.provider
        check(!isFrozen) { "Round info line display registry is frozen" }
        require(ruleModuleId.isNotBlank()) { "Round info provider rule module ID must not be blank" }
        require(providers.putIfAbsent(ruleModuleId, provider) == null) {
            "Duplicate round info provider: $ruleModuleId"
        }
    }

    override fun find(key: String): RoundInfoLineDisplay? = displays[key]

    override fun buildLines(ruleModuleId: String, tableState: TableState): List<RoundInfoLine> = providers[ruleModuleId]?.buildLines(tableState).orEmpty()

    override fun freeze() {
        isFrozen = true
    }
}
