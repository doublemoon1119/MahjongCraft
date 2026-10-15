package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** [PublicPlayerIndicatorDisplayRegistry] 的記憶體實作。 */
class PublicPlayerIndicatorDisplayRegistryImpl : PublicPlayerIndicatorDisplayRegistry {
    private val displays = mutableMapOf<String, PublicPlayerIndicatorDisplay>()

    override val registrationKeys: Set<String> get() = displays.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: PublicPlayerIndicatorDisplayDefinition) {
        val id = definition.id
        val display = definition.display
        check(!isFrozen) { "Public player indicator display registry is frozen" }
        NamespacedId.requireValid(id) { "Public player indicator display ID must be namespaced: $id" }
        require(displays.putIfAbsent(id, display) == null) { "Duplicate public player indicator display ID: $id" }
    }

    override fun find(id: String): PublicPlayerIndicatorDisplay? = displays[id]

    override fun freeze() {
        isFrozen = true
    }
}
