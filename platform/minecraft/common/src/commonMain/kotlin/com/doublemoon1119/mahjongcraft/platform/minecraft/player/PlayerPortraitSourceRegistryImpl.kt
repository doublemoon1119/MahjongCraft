package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** [PlayerPortraitSourceRegistry] 的記憶體實作。priority 較高者先執行，同分依 ID 排序。 */
class PlayerPortraitSourceRegistryImpl : PlayerPortraitSourceRegistry {
    /**
     * provider 的排序資料。
     *
     * @property providerId provider 的 namespaced 識別碼。
     * @property priority provider 的優先順序。
     * @property provider 實際解析 portrait 的 provider。
     */
    private data class Entry(
        val providerId: String,
        val priority: Int,
        val provider: PlayerPortraitSourceProvider,
    )

    private val entries = mutableMapOf<String, Entry>()
    private var orderedEntries: List<Entry> = emptyList()

    override var isFrozen: Boolean = false
        private set
    override val providerIds: Set<String> get() = entries.keys

    override fun register(providerId: String, priority: Int, provider: PlayerPortraitSourceProvider) {
        check(!isFrozen) { "Player portrait source registry is frozen" }
        NamespacedId.requireValid(providerId) { "Player portrait provider ID must be namespaced: $providerId" }
        require(entries.putIfAbsent(providerId, Entry(providerId, priority, provider)) == null) {
            "Duplicate player portrait provider ID: $providerId"
        }
    }

    override fun resolve(context: PlayerPortraitSourceContext): ResolvedPlayerPortraitSource? {
        val candidates = if (isFrozen) orderedEntries else sortedEntries()
        candidates.forEach { entry ->
            val source = try {
                entry.provider.resolve(context)
            } catch (cause: Exception) {
                throw PlayerPortraitSourceProviderException(entry.providerId, cause)
            }
            source?.let {
                return ResolvedPlayerPortraitSource(entry.providerId, source)
            }
        }
        return null
    }

    override fun freeze() {
        if (isFrozen) return
        orderedEntries = sortedEntries()
        isFrozen = true
    }

    private fun sortedEntries(): List<Entry> = entries.values.sortedWith(
        compareByDescending<Entry> { it.priority }.thenBy { it.providerId },
    )
}
