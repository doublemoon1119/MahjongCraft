package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import kotlin.uuid.Uuid

/** Renderer 可安全解讀的玩家頭像來源；不向 extension 暴露 Minecraft render callback。 */
sealed interface PlayerPortraitSource {
    /** 使用玩家 skin 的正面臉部，不包含帽子 layer。 */
    data object PlayerSkinFace : PlayerPortraitSource

    /** 使用已註冊牌面 asset；適合 AI 或規則角色的純牌面 fallback。 */
    data class TileFace(val assetKey: String) : PlayerPortraitSource {
        init {
            require(assetKey.isNotBlank()) { "Portrait tile asset key must not be blank" }
        }
    }

    /** 使用 resource 中指定的正規化 UV 區域。 */
    data class TextureRegion(
        val resourceId: String,
        val u0: Float,
        val v0: Float,
        val u1: Float,
        val v1: Float,
    ) : PlayerPortraitSource {
        init {
            require(':' in resourceId) { "Portrait texture resource ID must be namespaced: $resourceId" }
            require(listOf(u0, v0, u1, v1).all { it.isFinite() && it in 0f..1f }) {
                "Portrait texture UV values must be finite and normalized"
            }
            require(u0 < u1 && v0 < v1) { "Portrait texture UV region must have positive size" }
        }
    }
}

/** 第三方 portrait provider 可見的規則中立玩家身分。 */
data class PlayerPortraitSourceContext(
    val playerId: Uuid,
    val isAi: Boolean,
)

/** 只解析宣告式來源；座位 actor 的生成、姿態、存檔與清理未來由獨立 SeatActorProvider 負責。 */
fun interface PlayerPortraitSourceProvider {
    fun resolve(context: PlayerPortraitSourceContext): PlayerPortraitSource?
}

/** 帶有 provider 身分的解析結果，供平台在資源失敗時去重記錄診斷。 */
data class ResolvedPlayerPortraitSource(
    val providerId: String,
    val source: PlayerPortraitSource,
)

/** 第三方 provider 在解析期失敗；平台可依 [providerId] 去重警告並套用內建 fallback。 */
class PlayerPortraitSourceProviderException(
    val providerId: String,
    cause: Throwable,
) : IllegalStateException("Player portrait provider failed: $providerId", cause)

/** 依 priority 與穩定 provider ID 解析玩家 portrait 的凍結式 registry。 */
interface PlayerPortraitSourceRegistry {
    val isFrozen: Boolean
    val providerIds: Set<String>

    fun register(providerId: String, priority: Int = 0, provider: PlayerPortraitSourceProvider)
    fun resolve(context: PlayerPortraitSourceContext): ResolvedPlayerPortraitSource?
    fun freeze()
}

/** [PlayerPortraitSourceRegistry] 的記憶體實作。priority 較高者先執行，同分依 ID 排序。 */
class PlayerPortraitSourceRegistryImpl : PlayerPortraitSourceRegistry {
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
        require(':' in providerId) { "Player portrait provider ID must be namespaced: $providerId" }
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
