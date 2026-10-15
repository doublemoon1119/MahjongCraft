package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.coroutines.cancellation.CancellationException

/**
 * [PlayerPortraitSourceRegistry] 的記憶體實作。priority 較高者先執行，同分依 ID 排序。
 *
 * @property failurePolicy 判斷 provider 丟出的失敗能否包成 [PlayerPortraitSourceProviderException] 交給呼叫端降級；
 * 無法隔離的失敗原樣往外傳。
 */
@Single(binds = [PlayerPortraitSourceRegistry::class])
class PlayerPortraitSourceRegistryImpl(
    @Provided private val failurePolicy: ExtensionFailurePolicy,
) : PlayerPortraitSourceRegistry {
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

    /** 以來源識別碼索引的登記項目。 */
    private val entries = mutableMapOf<String, Entry>()

    /** 凍結後固定使用的解析順序。 */
    private var orderedEntries: List<Entry> = emptyList()

    override var isFrozen: Boolean = false
        private set

    override val providerIds: Set<String> get() = entries.keys.toSet()

    override fun register(definition: PlayerPortraitSourceDefinition) {
        val providerId = definition.providerId
        val priority = definition.priority
        val provider = definition.provider
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Throwable) {
                if (!failurePolicy.isIsolatable(cause)) throw cause
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

    /**
     * 依優先順序由高至低、同分依來源識別碼排序。
     *
     * @return 登記項目的解析順序。
     */
    private fun sortedEntries(): List<Entry> = entries.values.sortedWith(
        compareByDescending<Entry> { it.priority }.thenBy { it.providerId },
    )
}
