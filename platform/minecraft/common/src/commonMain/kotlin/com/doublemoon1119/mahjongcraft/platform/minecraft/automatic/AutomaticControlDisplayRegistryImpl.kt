package com.doublemoon1119.mahjongcraft.platform.minecraft.automatic

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/** [AutomaticControlDisplayRegistry] 的記憶體實作。 */
class AutomaticControlDisplayRegistryImpl : AutomaticControlDisplayRegistry {
    /** 依自動操作識別碼索引的顯示資料。 */
    private val displays = mutableMapOf<String, AutomaticControlDisplay>()

    override val registrationKeys: Set<String>
        get() = displays.keys.toSet()

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: AutomaticControlDisplayDefinition) {
        val controlId = definition.controlId
        val display = definition.display
        check(!isFrozen) { "Automatic control display registry is frozen" }
        MinecraftResourceIds.requireValid(controlId) { "Automatic control ID must be namespaced: $controlId" }
        require(displays.putIfAbsent(controlId, display) == null) {
            "Automatic control display already registered: $controlId"
        }
    }

    override fun find(controlId: String): AutomaticControlDisplay? = displays[controlId]

    override fun freeze() {
        isFrozen = true
    }
}
