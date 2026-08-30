package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import kotlin.uuid.Uuid

/** RoomScreen 可安全解讀的成員外觀來源。 */
sealed interface RoomMemberAppearanceSource {
    data object PlayerModel : RoomMemberAppearanceSource
    data object Portrait : RoomMemberAppearanceSource
    data class ActorPreview(val actorKey: String) : RoomMemberAppearanceSource {
        init {
            require(':' in actorKey) { "Room actor preview key must be namespaced: $actorKey" }
        }
    }
}

/** 房間成員外觀 provider 可見的公開身分。 */
data class RoomMemberAppearanceContext(val playerId: Uuid, val isAi: Boolean)

fun interface RoomMemberAppearanceSourceProvider {
    fun resolve(context: RoomMemberAppearanceContext): RoomMemberAppearanceSource?
}

/** 保留失敗 provider ID，讓平台可去重記錄後安全 fallback。 */
class RoomMemberAppearanceSourceProviderException(
    val providerId: String,
    cause: Throwable,
) : RuntimeException("Room member appearance provider failed: $providerId", cause)

/** 依 priority 與 provider ID 解析房間成員外觀的凍結式 registry。 */
interface RoomMemberAppearanceSourceRegistry {
    val isFrozen: Boolean
    fun register(providerId: String, priority: Int = 0, provider: RoomMemberAppearanceSourceProvider)
    fun resolve(context: RoomMemberAppearanceContext): RoomMemberAppearanceSource
    fun freeze()
}

/** [RoomMemberAppearanceSourceRegistry] 的記憶體實作。 */
class RoomMemberAppearanceSourceRegistryImpl : RoomMemberAppearanceSourceRegistry {
    private data class Entry(val id: String, val priority: Int, val provider: RoomMemberAppearanceSourceProvider)

    private val entries = mutableMapOf<String, Entry>()
    private var ordered = emptyList<Entry>()
    override var isFrozen: Boolean = false
        private set

    override fun register(providerId: String, priority: Int, provider: RoomMemberAppearanceSourceProvider) {
        check(!isFrozen) { "Room member appearance registry is frozen" }
        require(':' in providerId) { "Room appearance provider ID must be namespaced: $providerId" }
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

    private fun sorted(): List<Entry> = entries.values.sortedWith(compareByDescending<Entry> { it.priority }.thenBy { it.id })
}
