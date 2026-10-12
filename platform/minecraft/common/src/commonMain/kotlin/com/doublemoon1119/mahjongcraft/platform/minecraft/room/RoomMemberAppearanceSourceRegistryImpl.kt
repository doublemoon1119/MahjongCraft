package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** [RoomMemberAppearanceSourceRegistry] 的記憶體實作。 */
class RoomMemberAppearanceSourceRegistryImpl : RoomMemberAppearanceSourceRegistry {
    /**
     * provider 的排序資料。
     *
     * @property id provider 的穩定識別碼。
     * @property priority provider 的解析優先順序。
     * @property provider 執行來源解析的 provider。
     */
    private data class Entry(val id: String, val priority: Int, val provider: RoomMemberAppearanceSourceProvider)

    private val entries = mutableMapOf<String, Entry>()

    override val registrationKeys: Set<String> get() = entries.keys.toSet()
    private var ordered = emptyList<Entry>()
    override var isFrozen: Boolean = false
        private set

    override fun register(providerId: String, priority: Int, provider: RoomMemberAppearanceSourceProvider) {
        check(!isFrozen) { "Room member appearance registry is frozen" }
        NamespacedId.requireValid(providerId) { "Room appearance provider ID must be namespaced: $providerId" }
        require(entries.putIfAbsent(providerId, Entry(providerId, priority, provider)) == null) {
            "Duplicate room appearance provider ID: $providerId"
        }
    }

    override fun resolve(context: RoomMemberAppearanceContext): RoomMemberAppearanceSource {
        val candidates = if (isFrozen) ordered else sorted()
        return candidates.firstNotNullOfOrNull { entry ->
            try {
                entry.provider.resolve(context)
            } catch (cause: Throwable) {
                throw RoomMemberAppearanceSourceProviderException(entry.id, cause)
            }
        }
            ?: if (context.isAi) RoomMemberAppearanceSource.Portrait else RoomMemberAppearanceSource.PlayerModel
    }

    override fun freeze() {
        if (isFrozen) return
        ordered = sorted()
        isFrozen = true
    }

    /**
     * 依優先順序由高至低、同分依 provider ID 排序。
     *
     * @return 登記項目的解析順序。
     */
    private fun sorted(): List<Entry> = entries.values.sortedWith(compareByDescending<Entry> { it.priority }.thenBy { it.id })
}
