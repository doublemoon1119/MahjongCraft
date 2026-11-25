package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.player

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.ServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.MahjongPlayerIdentitySavedData
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.computeSavedData
import net.minecraft.server.level.ServerPlayer
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * 管理跨維度共用的伺服器級玩家普通名稱索引。
 *
 * @property serverHolder 取得目前伺服器及其 overworld 存檔服務。
 */
class ServerPlayerIdentityStore(
    private val serverHolder: ServerHolder,
) {
    /** 供非伺服器執行緒讀取的最後已知名稱快照。 */
    @Volatile
    private var cachedNames: Map<Uuid, String> = emptyMap()

    /** 載入目前伺服器存檔的名稱快照。必須在伺服器主執行緒呼叫。 */
    fun refresh() {
        cachedNames = state()?.snapshot().orEmpty().mapKeys { it.key.toKotlinUuid() }
    }

    /**
     * 從目前伺服器存檔取得最後已知名稱。
     *
     * @param playerId 玩家 UUID。
     * @return 最後已知普通名稱。尚未記錄時為 null。
     */
    fun nameOf(playerId: Uuid): String? = cachedNames[playerId]

    /**
     * 依最後已知普通名稱尋找玩家 UUID。不向外部服務查詢。
     *
     * @param query 名稱片段，使用大小寫不敏感比對。
     * @return 名稱包含 [query] 的玩家 UUID。
     */
    fun findPlayerIdsByName(query: String): Set<Uuid> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return emptySet()
        return cachedNames.filterValues { it.lowercase().contains(normalized) }.keys
    }

    /**
     * 在伺服器主執行緒解析玩家普通名稱，並在看見線上玩家時更新持久化索引。
     *
     * @param playerId 玩家 UUID。
     * @return 線上 GameProfile、已保存名稱或原版 GameProfileCache 名稱。都不存在時為 null。
     */
    fun resolveKnownName(playerId: Uuid): String? {
        serverHolder.findPlayer(playerId)?.let { player ->
            val name = player.gameProfile.name.takeIf { it.isNotBlank() }
            if (name != null) {
                remember(playerId, name)
                return name
            }
        }
        nameOf(playerId)?.let { return it }
        val cached = serverHolder.current()?.profileCache?.get(playerId.toJavaUuid())?.orElse(null)?.name
        if (!cached.isNullOrBlank()) remember(playerId, cached)
        return cached
    }

    /**
     * 記錄目前登入玩家的普通 GameProfile 名稱。
     *
     * @param player 登入的伺服器玩家。
     * @return 名稱有變更且已寫入索引時為 true。
     */
    fun remember(player: ServerPlayer): Boolean = remember(player.uuid.toKotlinUuid(), player.gameProfile.name)

    /**
     * 依目前伺服器中的線上玩家資料記錄名稱。找不到玩家時安全略過。
     *
     * @param playerId 玩家 UUID。
     * @return 名稱有變更且已寫入索引時為 true。
     */
    fun rememberOnline(playerId: Uuid): Boolean = serverHolder.findPlayer(playerId)?.let(::remember) == true

    /**
     * 記錄指定 UUID 的普通名稱。無法取得伺服器或名稱無效時安全略過。
     *
     * @param playerId 玩家 UUID。
     * @param name 玩家普通名稱。
     * @return 名稱有變更且已寫入索引時為 true。
     */
    fun remember(playerId: Uuid, name: String): Boolean {
        val changed = state()?.remember(playerId.toJavaUuid(), name) == true
        if (changed) refresh()
        return changed
    }

    /** 取得目前伺服器 overworld 的持久化名稱索引。必須在伺服器主執行緒呼叫。 */
    private fun state(): MahjongPlayerIdentitySavedData? = serverHolder.current()?.overworld()?.dataStorage?.computeSavedData(
        key = MahjongPlayerIdentitySavedData.STORAGE_KEY,
        decode = MahjongPlayerIdentitySavedData::fromNbt,
        create = MahjongPlayerIdentitySavedData::create,
    )
}
