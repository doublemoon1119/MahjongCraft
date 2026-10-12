package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import org.koin.core.annotation.Single
/** [GameConfigPresentationRegistry] 的記憶體實作。 */
@Single(binds = [GameConfigPresentationRegistry::class])
class GameConfigPresentationRegistryImpl : GameConfigPresentationRegistry {
    private val definitions = linkedMapOf<String, GameConfigPresentationDefinition>()

    override var isFrozen: Boolean = false
        private set
    override val ruleModuleIds: Set<String> get() = definitions.keys

    override fun register(definition: GameConfigPresentationDefinition) {
        check(!isFrozen) { "Game config presentation registry is frozen" }
        require(definitions.putIfAbsent(definition.ruleModuleId, definition) == null) {
            "Duplicate game config presentation: ${definition.ruleModuleId}"
        }
    }

    override fun find(ruleModuleId: String): GameConfigPresentationDefinition? = definitions[ruleModuleId]

    override fun freeze() {
        isFrozen = true
    }
}
