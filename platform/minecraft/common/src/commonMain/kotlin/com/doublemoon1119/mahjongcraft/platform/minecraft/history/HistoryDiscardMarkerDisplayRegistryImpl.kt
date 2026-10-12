package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import org.koin.core.annotation.Single

/** [HistoryDiscardMarkerDisplayRegistry] 的記憶體實作。 */
@Single(binds = [HistoryDiscardMarkerDisplayRegistry::class])
class HistoryDiscardMarkerDisplayRegistryImpl : HistoryDiscardMarkerDisplayRegistry {
    private val displays = mutableMapOf<String, HistoryDiscardMarkerDisplay>()
    override var isFrozen: Boolean = false
        private set
    override val registrationKeys: Set<String> get() = displays.keys.toSet()

    override fun register(
        markerId: String,
        display: HistoryDiscardMarkerDisplay,
    ) {
        check(!isFrozen) { "History discard marker display registry is frozen" }
        NamespacedId.requireValid(markerId) { "History discard marker ID must be namespaced: $markerId" }
        require(displays.putIfAbsent(markerId, display) == null) { "Duplicate history discard marker display: $markerId" }
    }

    override fun find(markerId: String): HistoryDiscardMarkerDisplay? = displays[markerId]

    override fun freeze() {
        isFrozen = true
    }
}
