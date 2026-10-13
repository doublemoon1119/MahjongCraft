package com.doublemoon1119.mahjongcraft.platform.minecraft.showcase

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import org.koin.core.annotation.Single
/** [WinCelebrationShowcaseRegistry] 的記憶體實作。 */
@Single(binds = [WinCelebrationShowcaseRegistry::class])
class WinCelebrationShowcaseRegistryImpl : WinCelebrationShowcaseRegistry {
    /** 依 cue key 保存的胡牌演出定義。 */
    private val definitions = mutableMapOf<String, WinCelebrationShowcaseDefinition>()

    override var isFrozen: Boolean = false
        private set

    override val cueKeys: Set<String> get() = definitions.keys.toSet()

    override fun register(definition: WinCelebrationShowcaseDefinition) {
        check(!isFrozen) { "Win celebration showcase registry is frozen" }
        NamespacedId.requireValid(definition.cueKey) { "Showcase cue ID must be namespaced: ${definition.cueKey}" }
        require(definitions.putIfAbsent(definition.cueKey, definition.snapshot()) == null) {
            "Duplicate win celebration showcase cue: ${definition.cueKey}"
        }
    }

    override fun freeze() {
        isFrozen = true
    }

    override fun find(cueKey: String): WinCelebrationShowcaseDefinition? = definitions[cueKey]?.snapshot()

    /** 複製定義中的集合，避免呼叫端在登記後改變已凍結的演出。 */
    private fun WinCelebrationShowcaseDefinition.snapshot(): WinCelebrationShowcaseDefinition = copy(
        layers = layers.toSet(),
        extraSounds = extraSounds.toList(),
    )
}
