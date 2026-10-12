package com.doublemoon1119.mahjongcraft.platform.minecraft.sound

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import org.koin.core.annotation.Single

/** [GameActionSoundPresentationRegistry] 的記憶體實作。 */
@Single(binds = [GameActionSoundPresentationRegistry::class])
class GameActionSoundPresentationRegistryImpl : GameActionSoundPresentationRegistry {
    /** 依規則模組與動作 ID 索引的呈現定義。 */
    private val definitions = mutableMapOf<Pair<String, String>, GameActionSoundPresentation>()

    override var isFrozen: Boolean = false
        private set

    override val definitionKeys: Set<Pair<String, String>> get() = definitions.keys

    override fun register(definition: GameActionSoundDefinition) {
        check(!isFrozen) { "Game action sound presentation registry is frozen" }
        val key = definition.ruleModuleId to definition.actionId
        require(definitions.putIfAbsent(key, definition.presentation) == null) {
            "Game action sound presentation already registered: ${definition.ruleModuleId}/${definition.actionId}"
        }
    }

    override fun find(ruleModuleId: String, action: GameAction): GameActionSoundPresentation? = action.soundPresentationId()?.let { actionId -> definitions[ruleModuleId to actionId] }

    override fun freeze() {
        isFrozen = true
    }
}
