package com.doublemoon1119.mahjongcraft.platform.minecraft.showcase

/** [WinCelebrationShowcaseRegistry] 的記憶體實作。 */
class WinCelebrationShowcaseRegistryImpl : WinCelebrationShowcaseRegistry {
    private val definitions = mutableMapOf<String, WinCelebrationShowcaseDefinition>()
    override var isFrozen: Boolean = false
        private set
    override val cueKeys: Set<String> get() = definitions.keys.toSet()

    override fun register(definition: WinCelebrationShowcaseDefinition) {
        check(!isFrozen) { "Win celebration showcase registry is frozen" }
        require(definitions.putIfAbsent(definition.cueKey, definition) == null) {
            "Duplicate win celebration showcase cue: ${definition.cueKey}"
        }
    }

    override fun freeze() {
        isFrozen = true
    }

    override fun find(cueKey: String): WinCelebrationShowcaseDefinition? = definitions[cueKey]
}
