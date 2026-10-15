package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.coroutines.cancellation.CancellationException

/**
 * [RoomMemberAppearanceSourceRegistry] 的記憶體實作。
 *
 * @property failurePolicy 判斷 provider 丟出的失敗能否包成 [RoomMemberAppearanceSourceProviderException] 交給呼叫端降級；
 * 無法隔離的失敗原樣往外傳。
 */
@Single(binds = [RoomMemberAppearanceSourceRegistry::class])
class RoomMemberAppearanceSourceRegistryImpl(
    @Provided private val failurePolicy: ExtensionFailurePolicy,
) : RoomMemberAppearanceSourceRegistry {
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

    override fun register(definition: RoomMemberAppearanceSourceDefinition) {
        val providerId = definition.providerId
        val priority = definition.priority
        val provider = definition.provider
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Throwable) {
                if (!failurePolicy.isIsolatable(cause)) throw cause
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
